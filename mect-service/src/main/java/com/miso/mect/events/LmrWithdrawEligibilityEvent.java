package com.miso.mect.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LmrWithdrawEligibilityEvent {

    private String eventId;
    private String eventType;
    private Instant occurredAt;
    private String lmrId;
    private String planningYear;
    private Boolean canWithdraw;
    private String reason;
    private List<String> blockingFlags;
    private Instant updatedAt;

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
    public Boolean getCanWithdraw() { return canWithdraw; }
    public void setCanWithdraw(Boolean canWithdraw) { this.canWithdraw = canWithdraw; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<String> getBlockingFlags() { return blockingFlags; }
    public void setBlockingFlags(List<String> blockingFlags) { this.blockingFlags = blockingFlags; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
