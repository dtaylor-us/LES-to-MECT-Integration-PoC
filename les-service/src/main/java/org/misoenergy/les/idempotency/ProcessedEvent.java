package org.misoenergy.les.idempotency;

import jakarta.persistence.*;
import java.time.Instant;

/** Tracks consumed event IDs for idempotent processing. */
@Entity
@Table(name = "processed_event", indexes = @Index(unique = true, columnList = "event_id"))
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @PrePersist
    void processed() {
        if (processedAt == null) processedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
