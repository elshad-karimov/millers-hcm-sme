package az.millers.hcm.lifecycle.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

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

/**
 * Compliance audit row for a policy-acknowledgement / contract-signing /
 * document-collection onboarding task (M304 — Phase B.4). Captures who
 * acknowledged what, when, and the statement they agreed to. Recording one
 * marks the originating checklist task DONE.
 */
@Entity
@Table(name = "onboarding_acknowledgement", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class OnboardingAcknowledgement {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(name = "task_status_id", nullable = false, unique = true)
    private UUID taskStatusId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AcknowledgementKind kind;

    @Column(columnDefinition = "text")
    private String statement;

    @Column(length = 300)
    private String reference;

    @Column(name = "acknowledged_by", length = 80)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at", nullable = false)
    private OffsetDateTime acknowledgedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (acknowledgedAt == null) acknowledgedAt = OffsetDateTime.now();
    }
}
