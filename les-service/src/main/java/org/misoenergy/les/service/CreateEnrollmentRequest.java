package org.misoenergy.les.service;

import org.misoenergy.les.domain.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateEnrollmentRequest {

    @NotBlank
    private String lmrId;
    @NotBlank
    private String marketParticipantName;
    @NotBlank
    private String lmrName;
    @NotNull
    private ResourceType resourceType;
    @NotBlank
    private String planningYear;

    public String getLmrId() { return lmrId; }
    public void setLmrId(String lmrId) { this.lmrId = lmrId; }
    public String getMarketParticipantName() { return marketParticipantName; }
    public void setMarketParticipantName(String marketParticipantName) { this.marketParticipantName = marketParticipantName; }
    public String getLmrName() { return lmrName; }
    public void setLmrName(String lmrName) { this.lmrName = lmrName; }
    public ResourceType getResourceType() { return resourceType; }
    public void setResourceType(ResourceType resourceType) { this.resourceType = resourceType; }
    public String getPlanningYear() { return planningYear; }
    public void setPlanningYear(String planningYear) { this.planningYear = planningYear; }
}
