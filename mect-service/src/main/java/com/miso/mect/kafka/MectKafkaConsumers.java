package com.miso.mect.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miso.mect.events.LmrApprovedEvent;
import com.miso.mect.events.LmrWithdrawRequestedEvent;
import com.miso.mect.idempotency.ProcessedEvent;
import com.miso.mect.idempotency.ProcessedEventRepository;
import com.miso.mect.service.LMRService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MectKafkaConsumers {

    private static final Logger log = LoggerFactory.getLogger(MectKafkaConsumers.class);

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final LMRService lmrService;

    public MectKafkaConsumers(ObjectMapper objectMapper,
                              ProcessedEventRepository processedEventRepository,
                              LMRService lmrService) {
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
        this.lmrService = lmrService;
    }

    @KafkaListener(topics = "${mect.kafka.topics.approved}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onLmrApproved(String message) {
        try {
            LmrApprovedEvent evt = objectMapper.readValue(message, LmrApprovedEvent.class);
            if (processedEventRepository.existsByEventId(evt.getEventId())) return;
            ProcessedEvent pe = new ProcessedEvent();
            pe.setEventId(evt.getEventId());
            processedEventRepository.save(pe);

            lmrService.onApproved(evt.getEventId(), evt);
        } catch (Exception e) {
            log.error("Approved consumer error: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "${mect.kafka.topics.withdraw-requested}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onWithdrawRequested(String message) {
        try {
            LmrWithdrawRequestedEvent evt = objectMapper.readValue(message, LmrWithdrawRequestedEvent.class);
            if (processedEventRepository.existsByEventId(evt.getEventId())) return;
            ProcessedEvent pe = new ProcessedEvent();
            pe.setEventId(evt.getEventId());
            processedEventRepository.save(pe);

            lmrService.onWithdrawRequested(evt.getEventId(), evt);
        } catch (Exception e) {
            log.error("Withdraw requested consumer error: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
