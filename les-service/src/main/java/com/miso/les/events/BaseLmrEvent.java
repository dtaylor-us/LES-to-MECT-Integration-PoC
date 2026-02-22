package com.miso.les.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Common fields for all LMR Kafka events. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseLmrEvent {

    private String eventId;
    private String eventType;
    private Instant occurredAt;
    private String lmrId;
    private String planningYear;
    private String reason;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getLmrId() { return lmrId; }
    public void setLmrId(String lmrId) { this.lmrId = lmrId; }
    public String getPlanningYear() { return planningYear; }
    public void setPlanningYear(String planningYear) { this.planningYear = planningYear; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
