package az.millers.hcm.corehr.domain;

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

/**
 * Employee-initiated personal-info edit pending HR approval (M79 / P2-25).
 *
 * <p>One row per field changed — multiple rows can share the same workflow
 * instance if a UI flow submits a batch (e.g. email + phone together), but
 * the service applies them independently so partial approval is possible.
 */
@Entity
@Table(name = "personal_info_change_request", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class PersonalInfoChangeRequest {

    @Id
    private UUID id;

    @Column(name = "request_no", nullable = false, unique = true, length = 20)
    private String requestNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** Field key from the whitelisted set in the V64 CHECK. */
    @Column(name = "field_key", nullable = false, length = 80)
    private String fieldKey;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PersonalInfoChangeStatus status = PersonalInfoChangeStatus.PENDING;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "submitted_by", length = 80)
    private String submittedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "decided_by", length = 80)
    private String decidedBy;

    @Column(name = "decision_comment", columnDefinition = "text")
    private String decisionComment;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

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
        if (submittedAt == null) submittedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
