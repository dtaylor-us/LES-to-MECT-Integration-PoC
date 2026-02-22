package org.misoenergy.mect.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
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
                log.info("Outbox published to {} key={}", e.getTopic(), e.getMessageKey());
            } catch (Exception ex) {
                log.warn("Outbox publish failed id={} topic={}: {}", e.getId(), e.getTopic(), ex.getMessage());
            }
        }
    }
}
