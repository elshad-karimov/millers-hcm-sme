package az.millers.hcm.performance.domain;

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
import org.hibernate.annotations.TenantId;

/** HCM_12 M398 — one PIP check-in (PRD §20.3). */
@Entity
@Table(name = "pip_checkpoint", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class PipCheckpoint {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "pip_id", nullable = false)
    private UUID pipId;

    @Column(name = "checkpoint_date", nullable = false)
    private LocalDate checkpointDate;

    /** 1..5 progress rating. */
    @Column(name = "progress_rating")
    private Integer progressRating;

    @Column(name = "manager_comments", length = 2000)
    private String managerComments;

    @Column(name = "employee_comments", length = 2000)
    private String employeeComments;

    @Column(name = "recorded_by", length = 80)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        recordedAt = OffsetDateTime.now();
    }
}
