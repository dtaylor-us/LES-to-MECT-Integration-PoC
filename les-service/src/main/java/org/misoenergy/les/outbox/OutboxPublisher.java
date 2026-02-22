package org.misoenergy.les.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls outbox for unpublished events and publishes to Kafka.
 * Ensures at-least-once delivery; consumers use idempotency.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "2000")
    @Transactional
    public void publishUnpublished() {
        List<OutboxEntry> entries = outboxRepository.findUnpublished();
        for (OutboxEntry e : entries) {
            try {
                kafkaTemplate.send(e.getTopic(), e.getMessageKey(), e.getPayload()).get();
                e.setPublishedAt(Instant.now());
                outboxRepository.save(e);
                log.info("Outbox published event to {} key={}", e.getTopic(), e.getMessageKey());
            } catch (Exception ex) {
                log.warn("Outbox publish failed for id={} topic={}: {}", e.getId(), e.getTopic(), ex.getMessage());
            }
        }
    }
}
