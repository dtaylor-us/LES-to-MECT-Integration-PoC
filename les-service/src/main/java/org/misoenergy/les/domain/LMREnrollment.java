package org.misoenergy.les.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "lmr_enrollment", indexes = @Index(unique = true, columnList = "lmr_id"))
public class LMREnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lmr_id", nullable = false, unique = true, length = 64)
    private String lmrId;

    @Column(name = "market_participant_name", nullable = false, length = 256)
    private String marketParticipantName;

    @Column(name = "lmr_name", nullable = false, length = 256)
    private String lmrName;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 32)
    private ResourceType resourceType;

    @Column(name = "planning_year", nullable = false, length = 16)
    private String planningYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EnrollmentStatus status = EnrollmentStatus.DRAFT;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Reason from MECT when withdrawal is rejected (for UX). */
    @Column(name = "withdraw_reject_reason", length = 512)
    private String withdrawRejectReason;

    /** When MECT rejected withdrawal (e.g. state changed after button was shown). Visible to admins. */
    @Column(name = "withdraw_rejected_at")
    private Instant withdrawRejectedAt;

    @PrePersist
    void timestamps() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }

    // --- getters/setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public EnrollmentStatus getStatus() { return status; }
    public void setStatus(EnrollmentStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getWithdrawRejectReason() { return withdrawRejectReason; }
    public void setWithdrawRejectReason(String withdrawRejectReason) { this.withdrawRejectReason = withdrawRejectReason; }
    public Instant getWithdrawRejectedAt() { return withdrawRejectedAt; }
    public void setWithdrawRejectedAt(Instant withdrawRejectedAt) { this.withdrawRejectedAt = withdrawRejectedAt; }
}
