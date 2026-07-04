package az.millers.hcm.workflow.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Role-level absence cover (M176 / PRD §9.1).
 *
 * <p>While {@code start_date ≤ today ≤ end_date}, a user holding
 * {@code substituteRole} may act on any PENDING workflow step whose
 * {@code current_step_role = principalRole}. Multiple non-overlapping or
 * overlapping windows may coexist (e.g. two different substitutes for the
 * same principal during different periods).
 */
@Entity
@Table(name = "substitute_approver", schema = "workflow")
@Getter
@Setter
@NoArgsConstructor
public class SubstituteApprover {

    @Id
    private UUID id;

    @Column(name = "principal_role", nullable = false, length = 100)
    private String principalRole;

    @Column(name = "substitute_role", nullable = false, length = 100)
    private String substituteRole;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
