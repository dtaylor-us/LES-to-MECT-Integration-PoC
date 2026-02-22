package org.misoenergy.les.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.misoenergy.les.domain.LMRWithdrawEligibility;
import org.misoenergy.les.events.LmrWithdrawCompletedEvent;
import org.misoenergy.les.events.LmrWithdrawEligibilityEvent;
import org.misoenergy.les.events.LmrWithdrawRejectedEvent;
import org.misoenergy.les.idempotency.ProcessedEvent;
import org.misoenergy.les.idempotency.ProcessedEventRepository;
import org.misoenergy.les.repository.LMRWithdrawEligibilityRepository;
import org.misoenergy.les.service.EnrollmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * LES consumes: eligibility (read-model), withdraw completed, withdraw rejected.
 * Idempotency by eventId to avoid duplicate application.
 */
@Component
public class LesKafkaConsumers {

    private static final Logger log = LoggerFactory.getLogger(LesKafkaConsumers.class);

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final LMRWithdrawEligibilityRepository eligibilityRepository;
    private final EnrollmentService enrollmentService;

    @Value("${les.kafka.topics.eligibility}")
    private String topicEligibility;
    @Value("${les.kafka.topics.withdraw-completed}")
    private String topicWithdrawCompleted;
    @Value("${les.kafka.topics.withdraw-rejected}")
    private String topicWithdrawRejected;

    public LesKafkaConsumers(ObjectMapper objectMapper,
                            ProcessedEventRepository processedEventRepository,
                            LMRWithdrawEligibilityRepository eligibilityRepository,
                            EnrollmentService enrollmentService) {
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.enrollmentService = enrollmentService;
    }

    @KafkaListener(topics = "${les.kafka.topics.eligibility}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onEligibility(String message) {
        try {
            LmrWithdrawEligibilityEvent evt = objectMapper.readValue(message, LmrWithdrawEligibilityEvent.class);
            String eventId = evt.getEventId();
            if (eventId != null && processedEventRepository.existsByEventId(eventId)) {
                return;
            }
            if (eventId != null) {
                ProcessedEvent pe = new ProcessedEvent();
                pe.setEventId(eventId);
                processedEventRepository.save(pe);
            }

            String planningYear = evt.getPlanningYear();
            String lmrId = evt.getLmrId();
            if (planningYear == null || lmrId == null) return;

            Optional<LMRWithdrawEligibility> existing = eligibilityRepository.findByPlanningYearAndLmrId(planningYear, lmrId);
            LMRWithdrawEligibility el = existing.orElseGet(LMRWithdrawEligibility::new);
            el.setPlanningYear(planningYear);
            el.setLmrId(lmrId);
            el.setCanWithdraw(Boolean.TRUE.equals(evt.getCanWithdraw()));
            el.setReason(evt.getReason());
            el.setBlockingFlags(evt.getBlockingFlags() != null ? evt.getBlockingFlags() : List.of());
            if (evt.getUpdatedAt() != null) el.setUpdatedAt(evt.getUpdatedAt());
            eligibilityRepository.save(el);
            log.debug("Eligibility updated lmrId={} canWithdraw={}", lmrId, el.isCanWithdraw());
        } catch (Exception e) {
            log.error("Eligibility consumer error: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "${les.kafka.topics.withdraw-completed}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onWithdrawCompleted(String message) {
        try {
            LmrWithdrawCompletedEvent evt = objectMapper.readValue(message, LmrWithdrawCompletedEvent.class);
            if (processedEventRepository.existsByEventId(evt.getEventId())) return;
            ProcessedEvent pe = new ProcessedEvent();
            pe.setEventId(evt.getEventId());
            processedEventRepository.save(pe);

            enrollmentService.onWithdrawCompleted(evt.getEventId(), evt.getLmrId(), evt.getPlanningYear());
        } catch (Exception e) {
            log.error("Withdraw completed consumer error: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "${les.kafka.topics.withdraw-rejected}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onWithdrawRejected(String message) {
        try {
            LmrWithdrawRejectedEvent evt = objectMapper.readValue(message, LmrWithdrawRejectedEvent.class);
            if (processedEventRepository.existsByEventId(evt.getEventId())) return;
            ProcessedEvent pe = new ProcessedEvent();
            pe.setEventId(evt.getEventId());
            processedEventRepository.save(pe);

            enrollmentService.onWithdrawRejected(evt.getEventId(), evt.getLmrId(), evt.getPlanningYear(), evt.getReason());
        } catch (Exception e) {
            log.error("Withdraw rejected consumer error: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
