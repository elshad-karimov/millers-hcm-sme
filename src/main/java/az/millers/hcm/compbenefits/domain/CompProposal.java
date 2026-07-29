package az.millers.hcm.compbenefits.domain;

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
import org.hibernate.annotations.TenantId;

/**
 * A single per-employee compensation proposal inside a {@link CompCycle}
 * (M118).
 *
 * <p>The unique index on {@code (cycle_id, employee_id)} forces at most
 * one proposal per (cycle, employee). Approved proposals feed
 * {@code EmployeeCompensation} on the {@code effective_on} date.
 */
@Entity
@Table(name = "comp_proposal", schema = "compbenefits")
@Getter
@Setter
@NoArgsConstructor
public class CompProposal {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "current_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal currentSalary;

    @Column(name = "proposed_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal proposedSalary;

    @Column(nullable = false, length = 10)
    private String currency = "AZN";

    @Column(columnDefinition = "text")
    private String rationale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompProposalStatus status = CompProposalStatus.DRAFT;

    @Column(name = "proposed_by", length = 80)
    private String proposedBy;

    @Column(name = "proposed_at", nullable = false)
    private OffsetDateTime proposedAt;

    @Column(name = "decided_by", length = 80)
    private String decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    /** Day the new salary takes effect; null while DRAFT/SUBMITTED. */
    @Column(name = "effective_on")
    private LocalDate effectiveOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (proposedAt == null) proposedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
