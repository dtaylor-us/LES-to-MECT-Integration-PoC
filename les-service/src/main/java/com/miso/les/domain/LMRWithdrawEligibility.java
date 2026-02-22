package com.miso.les.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Local read-model of withdrawal eligibility (source: MECT via Kafka).
 * Keyed by planningYear:lmrId; updated when we consume lmr.withdraw.eligibility.v1.
 * {@code reason} is the user-facing message from MECT (e.g. why withdraw is blocked).
 * LES does not map blocking codes to text—display reason as-is.
 */
@Entity
@Table(name = "lmr_withdraw_eligibility",
       indexes = { @Index(unique = true, columnList = "planning_year, lmr_id") })
public class LMRWithdrawEligibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "planning_year", nullable = false, length = 16)
    private String planningYear;

    @Column(name = "lmr_id", nullable = false, length = 64)
    private String lmrId;

    @Column(name = "can_withdraw", nullable = false)
    private boolean canWithdraw;

    /** User-facing message from MECT when canWithdraw is false; display as-is. */
    @Column(name = "reason", length = 512)
    private String reason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "lmr_eligibility_blocking_flags", joinColumns = @JoinColumn(name = "eligibility_id"))
    @Column(name = "flag")
    private List<String> blockingFlags = new ArrayList<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }

    public static String cacheKey(String planningYear, String lmrId) {
        return planningYear + ":" + lmrId;
    }

    // --- getters/setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlanningYear() { return planningYear; }
    public void setPlanningYear(String planningYear) { this.planningYear = planningYear; }
    public String getLmrId() { return lmrId; }
    public void setLmrId(String lmrId) { this.lmrId = lmrId; }
    public boolean isCanWithdraw() { return canWithdraw; }
    public void setCanWithdraw(boolean canWithdraw) { this.canWithdraw = canWithdraw; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<String> getBlockingFlags() { return blockingFlags; }
    public void setBlockingFlags(List<String> blockingFlags) { this.blockingFlags = blockingFlags != null ? blockingFlags : new ArrayList<>(); }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
