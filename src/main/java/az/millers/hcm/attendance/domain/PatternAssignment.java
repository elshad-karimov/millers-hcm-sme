package az.millers.hcm.attendance.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.common.history.EffectiveDatedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Per-employee assignment of a {@link ShiftPattern} (M111).
 *
 * <p>Half-open date window {@code [startDate, endDate]} (inclusive). A null
 * {@code endDate} means "still in effect"; the partial unique index
 * {@code pattern_assignment_open_uk} forces at most one such open row per
 * (employee, pattern).
 *
 * <p>{@link #anchorDayIndex} positions the employee within the cycle on
 * {@code startDate}. Set to 0 for "start at the beginning of the cycle"
 * (the common case) or to a non-zero value to start a new hire mid-cycle
 * so the team's rotation stays coherent.
 */
@Entity
@Table(name = "pattern_assignment", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class PatternAssignment implements EffectiveDatedRecord {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "pattern_id", nullable = false)
    private UUID patternId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Null = open (still in effect). */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "anchor_day_index", nullable = false)
    private int anchorDayIndex = 0;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    @Override public LocalDate getEffectiveFrom() { return startDate; }
    @Override public LocalDate getEffectiveTo()   { return endDate; }
    @Override public void setEffectiveTo(LocalDate to) { this.endDate = to; }
}
