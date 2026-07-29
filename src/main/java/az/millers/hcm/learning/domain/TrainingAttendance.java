package az.millers.hcm.learning.domain;

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
import org.hibernate.annotations.TenantId;

/** HCM_14 M405 — per-employee session enrolment + attendance (PRD 14 §11). */
@Entity
@Table(name = "training_attendance", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class TrainingAttendance {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** ENROLLED | ATTENDED | LATE | NO_SHOW | CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "ENROLLED";

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
