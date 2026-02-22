package com.miso.mect.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.mect.domain.BlockingFlag;
import com.miso.mect.domain.LMR;
import com.miso.mect.domain.LmrStatus;
import com.miso.mect.events.*;
import com.miso.mect.outbox.OutboxEntry;
import com.miso.mect.outbox.OutboxRepository;
import com.miso.mect.repository.LMRRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * MECT is the authority: creates LMRs from approval events, decides withdraw outcome,
 * publishes eligibility so LES can maintain its read-model.
 */
@Service
public class LMRService {

    private static final Logger log = LoggerFactory.getLogger(LMRService.class);

    private final LMRRepository lmrRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${mect.kafka.topics.eligibility}")
    private String topicEligibility;
    @Value("${mect.kafka.topics.withdraw-completed}")
    private String topicWithdrawCompleted;
    @Value("${mect.kafka.topics.withdraw-rejected}")
    private String topicWithdrawRejected;

    public LMRService(LMRRepository lmrRepository, OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.lmrRepository = lmrRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Create LMR from approval event; compute deterministic seasonal capacity; publish eligibility.
     */
    @Transactional
    public void onApproved(String eventId, LmrApprovedEvent evt) {
        String lmrId = evt.getLmrId();
        String planningYear = evt.getPlanningYear();
        if (lmrRepository.existsByLmrIdAndPlanningYear(lmrId, planningYear)) {
            log.debug("LMR already exists, skip lmrId={} planningYear={}", lmrId, planningYear);
            return;
        }
        LMR lmr = new LMR();
        lmr.setLmrId(lmrId);
        lmr.setPlanningYear(planningYear);
        lmr.setStatus(LmrStatus.ACTIVE);
        Map<String, Double> cap = computeSeasonalCapacity(lmrId);
        lmr.setSeasonalCapacity(cap);
        lmrRepository.save(lmr);
        log.info("LMR created lmrId={} eventId={} capacity={}", lmrId, eventId, cap);

        publishEligibility(lmrId, planningYear, true, null, lmr.getBlockingFlags());
    }

    /** base = hash(lmrId) % 50 + 10; summer=base, fall=base-2, winter=base-1, spring=base-3 */
    private Map<String, Double> computeSeasonalCapacity(String lmrId) {
        int base = Math.abs(lmrId.hashCode() % 50) + 10;
        Map<String, Double> cap = new LinkedHashMap<>();
        cap.put("SUMMER", (double) base);
        cap.put("FALL", (double) base - 2);
        cap.put("WINTER", (double) base - 1);
        cap.put("SPRING", (double) base - 3);
        return cap;
    }

    /**
     * Process withdrawal request: if any blocking flags, reject; else withdraw and publish completed + eligibility.
     */
    @Transactional
    public void onWithdrawRequested(String eventId, LmrWithdrawRequestedEvent evt) {
        String lmrId = evt.getLmrId();
        String planningYear = evt.getPlanningYear();
        Optional<LMR> opt = lmrRepository.findByLmrIdAndPlanningYear(lmrId, planningYear);
        if (opt.isEmpty()) {
            log.warn("Withdraw requested for unknown LMR lmrId={} planningYear={}", lmrId, planningYear);
            String msg = EligibilityMessages.LMR_NOT_FOUND;
            publishRejected(eventId, lmrId, planningYear, msg);
            return;
        }
        LMR lmr = opt.get();
        if (lmr.getStatus() == LmrStatus.WITHDRAWN) {
            String msg = EligibilityMessages.ALREADY_WITHDRAWN;
            publishRejected(eventId, lmrId, planningYear, msg);
            return;
        }
        if (!lmr.getBlockingFlags().isEmpty()) {
            String reason = EligibilityMessages.reasonForFlags(lmr.getBlockingFlags());
            publishRejected(eventId, lmrId, planningYear, reason);
            publishEligibility(lmrId, planningYear, false, reason, lmr.getBlockingFlags());
            log.info("Withdraw rejected lmrId={} reason={}", lmrId, reason);
            return;
        }
        lmr.setStatus(LmrStatus.WITHDRAWN);
        lmr.getSeasonalCapacity().clear();
        lmrRepository.save(lmr);
        publishWithdrawCompleted(eventId, lmrId, planningYear);
        publishEligibility(lmrId, planningYear, false, EligibilityMessages.WITHDRAWN, Collections.emptySet());
        log.info("Withdraw completed lmrId={} eventId={}", lmrId, eventId);
    }

    /** Recompute eligibility for an LMR and publish (e.g. after flag enable/disable). */
    @Transactional
    public void recomputeAndPublishEligibility(String lmrId, String planningYear) {
        Optional<LMR> opt = lmrRepository.findByLmrIdAndPlanningYear(lmrId, planningYear);
        if (opt.isEmpty()) return;
        LMR lmr = opt.get();
        boolean canWithdraw = lmr.getStatus() == LmrStatus.ACTIVE && lmr.getBlockingFlags().isEmpty();
        String reason = canWithdraw ? null : (lmr.getStatus() == LmrStatus.WITHDRAWN
                ? EligibilityMessages.WITHDRAWN
                : EligibilityMessages.reasonForFlags(lmr.getBlockingFlags()));
        publishEligibility(lmrId, planningYear, canWithdraw, reason, lmr.getBlockingFlags());
    }

    @Transactional
    public Optional<LMR> enableFlag(String lmrId, String planningYear, BlockingFlag flag) {
        Optional<LMR> opt = lmrRepository.findByLmrIdAndPlanningYear(lmrId, planningYear);
        if (opt.isEmpty()) return Optional.empty();
        LMR lmr = opt.get();
        lmr.getBlockingFlags().add(flag);
        lmrRepository.save(lmr);
        recomputeAndPublishEligibility(lmrId, planningYear);
        return Optional.of(lmr);
    }

    @Transactional
    public Optional<LMR> disableFlag(String lmrId, String planningYear, BlockingFlag flag) {
        Optional<LMR> opt = lmrRepository.findByLmrIdAndPlanningYear(lmrId, planningYear);
        if (opt.isEmpty()) return Optional.empty();
        LMR lmr = opt.get();
        lmr.getBlockingFlags().remove(flag);
        lmrRepository.save(lmr);
        recomputeAndPublishEligibility(lmrId, planningYear);
        return Optional.of(lmr);
    }

    public Optional<LMR> findByLmrIdAndPlanningYear(String lmrId, String planningYear) {
        return lmrRepository.findByLmrIdAndPlanningYear(lmrId, planningYear);
    }

    private void publishEligibility(String lmrId, String planningYear, boolean canWithdraw, String reason, Set<BlockingFlag> flags) {
        LmrWithdrawEligibilityEvent evt = new LmrWithdrawEligibilityEvent();
        evt.setEventId(UUID.randomUUID().toString());
        evt.setEventType("lmr.withdraw.eligibility.v1");
        evt.setOccurredAt(Instant.now());
        evt.setUpdatedAt(Instant.now());
        evt.setLmrId(lmrId);
        evt.setPlanningYear(planningYear);
        evt.setCanWithdraw(canWithdraw);
        evt.setReason(reason);
        evt.setBlockingFlags(flags == null ? List.of() : flags.stream().map(Enum::name).toList());
        writeOutbox(topicEligibility, planningYear + ":" + lmrId, evt);
    }

    private void publishWithdrawCompleted(String eventId, String lmrId, String planningYear) {
        LmrWithdrawCompletedEvent evt = new LmrWithdrawCompletedEvent();
        evt.setEventId(UUID.randomUUID().toString());
        evt.setEventType("lmr.withdraw.completed.v1");
        evt.setOccurredAt(Instant.now());
        evt.setLmrId(lmrId);
        evt.setPlanningYear(planningYear);
        writeOutbox(topicWithdrawCompleted, planningYear + ":" + lmrId, evt);
    }

    private void publishRejected(String requestEventId, String lmrId, String planningYear, String reason) {
        LmrWithdrawRejectedEvent evt = new LmrWithdrawRejectedEvent();
        evt.setEventId(UUID.randomUUID().toString());
        evt.setEventType("lmr.withdraw.rejected.v1");
        evt.setOccurredAt(Instant.now());
        evt.setLmrId(lmrId);
        evt.setPlanningYear(planningYear);
        evt.setReason(reason);
        writeOutbox(topicWithdrawRejected, planningYear + ":" + lmrId, evt);
    }

    private void writeOutbox(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutboxEntry entry = new OutboxEntry();
            entry.setTopic(topic);
            entry.setMessageKey(key);
            entry.setPayload(json);
            outboxRepository.save(entry);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Outbox serialization failed", e);
        }
    }
}
