package org.misoenergy.mect.events;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LmrApprovedEvent extends BaseLmrEvent {

    private String marketParticipantName;
    private String lmrName;
    private String resourceType;

    public String getMarketParticipantName() { return marketParticipantName; }
    public void setMarketParticipantName(String marketParticipantName) { this.marketParticipantName = marketParticipantName; }
    public String getLmrName() { return lmrName; }
    public void setLmrName(String lmrName) { this.lmrName = lmrName; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
}
