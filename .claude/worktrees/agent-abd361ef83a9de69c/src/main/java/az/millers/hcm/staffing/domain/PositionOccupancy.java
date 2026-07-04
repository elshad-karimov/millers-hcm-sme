package az.millers.hcm.staffing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** M246 / PRD §15 — one occupancy row per (position × employee × window). */
@Entity
@Table(name = "position_occupancy", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class PositionOccupancy {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "occupancy_type", nullable = false, length = 32)
    private OccupancyType occupancyType = OccupancyType.PRIMARY;

    @Column(name = "fte_allocation", precision = 4, scale = 2, nullable = false)
    private BigDecimal fteAllocation = BigDecimal.ONE;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "end_reason", length = 64)
    private String endReason;

    @Column(name = "end_notes")
    private String endNotes;

    @Column(name = "home_position_id")
    private UUID homePositionId;

    @Column(name = "acting_allowance", precision = 14, scale = 2)
    private BigDecimal actingAllowance;

    @Column(name = "acting_allowance_currency", length = 3)
    private String actingAllowanceCurrency;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (occupancyType == null) occupancyType = OccupancyType.PRIMARY;
        if (fteAllocation == null) fteAllocation = BigDecimal.ONE;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public boolean isActive() {
        return endDate == null;
    }
}
