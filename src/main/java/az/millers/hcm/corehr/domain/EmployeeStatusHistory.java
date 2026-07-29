package az.millers.hcm.corehr.domain;

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
 * One slice of an employee's {@link EmploymentStatus} timeline (M62 / P1-11).
 *
 * <p>Written by:
 * <ul>
 *   <li>{@code EmployeeService.create()} — initial slice at hire (status =
 *       ON_PROBATION typically)</li>
 *   <li>{@code EmployeeService.changeStatus()} — every administrative transition</li>
 *   <li>{@code TerminationService} on processing — final TERMINATED slice</li>
 *   <li>{@code ContractChangeService} when a contract change implies a status
 *       change (e.g. probation confirmation → ACTIVE)</li>
 * </ul>
 *
 * <p>The partial unique index in V50 enforces "at most one open row per
 * employee", and the CHECK constraint pins the {@code status} column to
 * the valid enum string set.
 */
@Entity
@Table(name = "employee_status_history", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeStatusHistory implements EffectiveDatedRecord {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmploymentStatus status;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "source_module", length = 40)
    private String sourceModule;

    @Column(name = "source_entity", length = 60)
    private String sourceEntity;

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
