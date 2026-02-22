package org.misoenergy.les.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.misoenergy.les.domain.*;
import org.misoenergy.les.events.LmrApprovedEvent;
import org.misoenergy.les.events.LmrWithdrawRequestedEvent;
import org.misoenergy.les.outbox.OutboxEntry;
import org.misoenergy.les.outbox.OutboxRepository;
import org.misoenergy.les.repository.LMREnrollmentRepository;
import org.misoenergy.les.repository.LMRWithdrawEligibilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    private final LMREnrollmentRepository enrollmentRepository;
    private final LMRWithdrawEligibilityRepository eligibilityRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${les.kafka.topics.approved}")
    private String topicApproved;
    @Value("${les.kafka.topics.withdraw-requested}")
    private String topicWithdrawRequested;

    public EnrollmentService(LMREnrollmentRepository enrollmentRepository,
                             LMRWithdrawEligibilityRepository eligibilityRepository,
                             OutboxRepository outboxRepository,
                             ObjectMapper objectMapper) {
        this.enrollmentRepository = enrollmentRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LMREnrollment create(CreateEnrollmentRequest req) {
        if (enrollmentRepository.existsByLmrId(req.getLmrId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "LMR already exists: " + req.getLmrId());
        }
        LMREnrollment e = new LMREnrollment();
        e.setLmrId(req.getLmrId());
        e.setMarketParticipantName(req.getMarketParticipantName());
        e.setLmrName(req.getLmrName());
        e.setResourceType(req.getResourceType());
        e.setPlanningYear(req.getPlanningYear());
        e.setStatus(EnrollmentStatus.DRAFT);
        return enrollmentRepository.save(e);
    }

    @Transactional
    public LMREnrollment submit(String lmrId) {
        LMREnrollment e = enrollmentRepository.findByLmrId(lmrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "LMR not found: " + lmrId));
        if (e.getStatus() != EnrollmentStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only DRAFT can be submitted");
        }
        e.setStatus(EnrollmentStatus.SUBMITTED);
        return enrollmentRepository.save(e);
    }

    @Transactional
    public LMREnrollment approve(String lmrId) {
        LMREnrollment e = enrollmentRepository.findByLmrId(lmrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "LMR not found: " + lmrId));
        if (e.getStatus() != EnrollmentStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only SUBMITTED can be approved");
        }
        e.setStatus(EnrollmentStatus.APPROVED);
        enrollmentRepository.save(e);

        LmrApprovedEvent evt = new LmrApprovedEvent();
        evt.setEventId(UUID.randomUUID().toString());
        evt.setEventType("lmr.approved.v1");
        evt.setOccurredAt(Instant.now());
        evt.setLmrId(e.getLmrId());
        evt.setPlanningYear(e.getPlanningYear());
        evt.setMarketParticipantName(e.getMarketParticipantName());
        evt.setLmrName(e.getLmrName());
        evt.setResourceType(e.getResourceType().name());
        writeToOutbox(topicApproved, e.getPlanningYear() + ":" + e.getLmrId(), evt);
        log.info("Enrollment approved and event queued lmrId={} eventId={}", e.getLmrId(), evt.getEventId());
        return e;
    }

    /**
     * Withdraw: check local eligibility (no sync call to MECT). If canWithdraw, emit request; else 409.
     */
    @Transactional
    public LMREnrollment withdraw(String lmrId) {
        LMREnrollment e = enrollmentRepository.findByLmrId(lmrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "LMR not found: " + lmrId));
        if (e.getStatus() != EnrollmentStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only APPROVED enrollments can be withdrawn; current: " + e.getStatus());
        }

        Optional<LMRWithdrawEligibility> opt = eligibilityRepository.findByPlanningYearAndLmrId(e.getPlanningYear(), e.getLmrId());
        if (opt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Eligibility unknown; MECT may not have processed approval yet.");
        }
        LMRWithdrawEligibility el = opt.get();
        if (!el.isCanWithdraw()) {
            // Message from MECT; LES does not map codes—display as-is for the user
            String message = el.getReason() != null ? el.getReason() : "Withdrawal is not allowed.";
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }

        e.setStatus(EnrollmentStatus.WITHDRAWN_REQUESTED);
        enrollmentRepository.save(e);

        LmrWithdrawRequestedEvent evt = new LmrWithdrawRequestedEvent();
        evt.setEventId(UUID.randomUUID().toString());
        evt.setEventType("lmr.withdraw.requested.v1");
        evt.setOccurredAt(Instant.now());
        evt.setLmrId(e.getLmrId());
        evt.setPlanningYear(e.getPlanningYear());
        writeToOutbox(topicWithdrawRequested, e.getPlanningYear() + ":" + e.getLmrId(), evt);
        log.info("Withdraw requested lmrId={} eventId={}", e.getLmrId(), evt.getEventId());
        return e;
    }

    public Optional<LMRWithdrawEligibility> getEligibility(String lmrId) {
        return enrollmentRepository.findByLmrId(lmrId)
                .flatMap(en -> eligibilityRepository.findByPlanningYearAndLmrId(en.getPlanningYear(), en.getLmrId()));
    }

    private void writeToOutbox(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutboxEntry entry = new OutboxEntry();
            entry.setTopic(topic);
            entry.setMessageKey(key);
            entry.setPayload(json);
            outboxRepository.save(entry);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Outbox serialization failed", ex);
        }
    }

    /** Called by Kafka consumer when MECT confirms withdrawal. */
    @Transactional
    public void onWithdrawCompleted(String eventId, String lmrId, String planningYear) {
        enrollmentRepository.findByLmrId(lmrId).ifPresent(e -> {
            if (e.getStatus() == EnrollmentStatus.WITHDRAWN_REQUESTED) {
                e.setStatus(EnrollmentStatus.WITHDRAWN);
                e.setWithdrawRejectReason(null);
                enrollmentRepository.save(e);
                log.info("Withdraw completed lmrId={} eventId={}", lmrId, eventId);
            }
        });
    }

    /**
     * Called by Kafka consumer when MECT rejects withdrawal (e.g. state changed after user clicked).
     * We record it so admins can see and act; no automatic reconciliation in the standard workflow.
     */
    @Transactional
    public void onWithdrawRejected(String eventId, String lmrId, String planningYear, String reason) {
        enrollmentRepository.findByLmrId(lmrId).ifPresent(e -> {
            if (e.getStatus() == EnrollmentStatus.WITHDRAWN_REQUESTED) {
                e.setStatus(EnrollmentStatus.WITHDRAW_REJECTED);
                e.setWithdrawRejectReason(reason);
                e.setWithdrawRejectedAt(Instant.now());
                enrollmentRepository.save(e);
                log.info("Withdraw rejected lmrId={} reason={} eventId={}", lmrId, reason, eventId);
            }
        });
    }

    public Optional<LMREnrollment> getByLmrId(String lmrId) {
        return enrollmentRepository.findByLmrId(lmrId);
    }

    public List<LMREnrollment> listAll() {
        return enrollmentRepository.findAllByOrderByUpdatedAtDesc();
    }

    /** For admins: enrollments where withdrawal was rejected by MECT (edge case—state changed after button was shown). */
    public List<LMREnrollment> listWithdrawRejected() {
        return enrollmentRepository.findByStatusOrderByWithdrawRejectedAtDesc(EnrollmentStatus.WITHDRAW_REJECTED);
    }

    /**
     * Admin correction: reset a WITHDRAW_REJECTED enrollment back to APPROVED so it remains active.
     * Clears rejection fields to avoid stale data being shown to users.
     * Only valid when the enrollment is in WITHDRAW_REJECTED state; any other state is rejected with 409.
     */
    @Transactional
    public LMREnrollment correctRejectedWithdrawal(String lmrId) {
        LMREnrollment e = enrollmentRepository.findByLmrId(lmrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "LMR not found: " + lmrId));
        if (e.getStatus() != EnrollmentStatus.WITHDRAW_REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only WITHDRAW_REJECTED enrollments can be corrected; current: " + e.getStatus());
        }
        e.setStatus(EnrollmentStatus.APPROVED);
        e.setWithdrawRejectReason(null);
        e.setWithdrawRejectedAt(null);
        enrollmentRepository.save(e);
        log.info("Admin corrected withdrawal rejection lmrId={}", lmrId);
        return e;
    }
}
