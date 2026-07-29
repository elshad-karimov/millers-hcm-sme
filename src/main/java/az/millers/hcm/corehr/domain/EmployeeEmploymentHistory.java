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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * One slice of an employee's position / department / manager / grade /
 * employment-type state (M62 / P1-10).
 *
 * <p>Written by:
 * <ul>
 *   <li>{@code EmployeeService.create()} — initial slice from {@code hire_date}</li>
 *   <li>{@code ContractChangeService.apply()} — every applied lifecycle change</li>
 * </ul>
 *
 * <p>The {@code uq_emp_hist_one_open_per_employee} partial unique index in V50
 * guarantees at most one row per employee has {@link #effectiveTo} = null.
 *
 * <p>Implements {@link EffectiveDatedRecord} so the "close prior slice" mutation
 * uses the shared default method.
 */
@Entity
@Table(name = "employee_employment_history", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeEmploymentHistory implements EffectiveDatedRecord {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "position_title", length = 160)
    private String positionTitle;

    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    @Column(name = "department_name", length = 160)
    private String departmentName;

    @Column(name = "cost_centre", length = 64)
    private String costCentre;

    @Column(name = "manager_id")
    private UUID managerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 20)
    private EmploymentType employmentType;

    @Column(name = "fte_percent", precision = 5, scale = 2)
    private BigDecimal ftePercent;

    @Column(name = "change_reason", columnDefinition = "text")
    private String changeReason;

    /** Originating subsystem, e.g. {@code "LIFECYCLE"} / {@code "CORE_HR"}. */
    @Column(name = "source_module", length = 40)
    private String sourceModule;

    /** Originating entity type, e.g. {@code "ContractChange"} / {@code "Employee"}. */
    @Column(name = "source_entity", length = 60)
    private String sourceEntity;

    /** Originating record id, stringified — schemas vary so no hard FK. */
    @Column(name = "source_id", length = 64)
    private String sourceId;

    @Column(name = "changed_by", length = 80)
    private String changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
