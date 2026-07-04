package az.millers.hcm.performance.domain;

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

/** HCM_12 M397 — one calibration-session committee member (PRD §19.1). */
@Entity
@Table(name = "calibration_committee_member", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class CalibrationCommitteeMember {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** CHAIR | MEMBER | OBSERVER | HR_FACILITATOR */
    @Column(name = "member_role", nullable = false, length = 20)
    private String memberRole = "MEMBER";

    @Column(name = "added_by", length = 80)
    private String addedBy;

    @Column(name = "added_at", nullable = false, updatable = false)
    private OffsetDateTime addedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        addedAt = OffsetDateTime.now();
    }
}
