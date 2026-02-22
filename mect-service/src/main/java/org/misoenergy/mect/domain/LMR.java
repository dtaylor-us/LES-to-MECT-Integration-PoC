package org.misoenergy.mect.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "lmr", indexes = @Index(unique = true, columnList = "lmr_id, planning_year"))
public class LMR {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lmr_id", nullable = false, length = 64)
    private String lmrId;

    @Column(name = "planning_year", nullable = false, length = 16)
    private String planningYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private LmrStatus status = LmrStatus.ACTIVE;

    /** Seasonal capacity in MW: season name -> MW value. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "lmr_seasonal_capacity", joinColumns = @JoinColumn(name = "lmr_entity_id", referencedColumnName = "id"))
    @MapKeyColumn(name = "season")
    @Column(name = "mw")
    private Map<String, Double> seasonalCapacity = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "lmr_blocking_flags", joinColumns = @JoinColumn(name = "lmr_entity_id", referencedColumnName = "id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "flag")
    private Set<BlockingFlag> blockingFlags = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLmrId() { return lmrId; }
    public void setLmrId(String lmrId) { this.lmrId = lmrId; }
    public String getPlanningYear() { return planningYear; }
    public void setPlanningYear(String planningYear) { this.planningYear = planningYear; }
    public LmrStatus getStatus() { return status; }
    public void setStatus(LmrStatus status) { this.status = status; }
    public Map<String, Double> getSeasonalCapacity() { return seasonalCapacity; }
    public void setSeasonalCapacity(Map<String, Double> seasonalCapacity) { this.seasonalCapacity = seasonalCapacity != null ? seasonalCapacity : new HashMap<>(); }
    public Set<BlockingFlag> getBlockingFlags() { return blockingFlags; }
    public void setBlockingFlags(Set<BlockingFlag> blockingFlags) { this.blockingFlags = blockingFlags != null ? blockingFlags : new HashSet<>(); }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
