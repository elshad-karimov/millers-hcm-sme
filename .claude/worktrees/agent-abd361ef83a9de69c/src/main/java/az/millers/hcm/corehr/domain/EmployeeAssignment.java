package az.millers.hcm.corehr.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.common.history.EffectiveDatedRecord;
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

/**
 * Employee assignment to a position (M75 / P2-20). Effective-dated via the
 * M62 {@link EffectiveDatedRecord} pattern. The scalar {@code employee.position_id}
 * stays as the "current primary" shortcut; this table carries the full
 * history plus all non-PRIMARY assignment types (acting, secondment, matrix,
 * project).
 *
 * <p>At most one open PRIMARY assignment per employee (V61 partial unique
 * index {@code uq_ea_one_open_primary}); multiple non-PRIMARY assignments
 * can co-exist with summed allocations not exceeding 100%.
 */
@Entity
@Table(name = "employee_assignment", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeAssignment implements EffectiveDatedRecord {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false, length = 20)
    private AssignmentType assignmentType = AssignmentType.PRIMARY;

    @Column(name = "allocation_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal allocationPercent = new BigDecimal("100.00");

    /**
     * Matrix / dotted-line manager for this specific assignment. Distinct
     * from {@code Employee.managerId} (the line manager on the primary).
     */
    @Column(name = "matrix_manager_id")
    private UUID matrixManagerId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
