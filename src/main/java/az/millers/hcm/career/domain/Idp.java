package az.millers.hcm.career.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import az.millers.hcm.common.expiry.ExpiryTrackable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Individual Development Plan (IDP) — M57.
 * One per employee, tracks the target role, skill gaps and training activities.
 */
@Entity
@Table(schema = "learning", name = "idp")
@Getter @Setter
public class Idp implements ExpiryTrackable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "target_role", nullable = false)
    private String targetRole;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(nullable = false)
    private String status = "DRAFT";   // DRAFT | ACTIVE | COMPLETED | CANCELLED

    @Column(name = "manager_comment")
    private String managerComment;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "idp", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IdpSkillGap> skillGaps = new ArrayList<>();

    @OneToMany(mappedBy = "idp", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IdpActivity> activities = new ArrayList<>();

    // ── ExpiryTrackable ──────────────────────────────────────────────────────

    @Override
    public LocalDate getExpiryDate() { return targetDate; }

    @Override
    public String getEntityLabel() { return "IDP Deadline"; }

    @Override
    public String getDisplayName() {
        return targetRole == null ? "(no target role)" : targetRole;
    }
}
