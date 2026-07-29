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
import org.hibernate.annotations.TenantId;

/**
 * A buddy / mentor pairing for a new hire's onboarding (M305 — Phase C.1).
 * Assigning one closes the originating BUDDY_TASK checklist task.
 */
@Entity
@Table(name = "onboarding_buddy", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class OnboardingBuddy {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "buddy_employee_id", nullable = false)
    private UUID buddyEmployeeId;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(name = "task_status_id")
    private UUID taskStatusId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private BuddyRole role = BuddyRole.BUDDY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private BuddyStatus status = BuddyStatus.ACTIVE;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "assigned_by", length = 80)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (assignedAt == null) assignedAt = OffsetDateTime.now();
    }
}
