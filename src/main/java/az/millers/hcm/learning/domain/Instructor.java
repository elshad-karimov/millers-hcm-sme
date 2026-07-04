package az.millers.hcm.learning.domain;

import java.math.BigDecimal;
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

/** HCM_14 M404 — internal (employee) or external trainer (PRD 14 §18). */
@Entity
@Table(name = "instructor", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class Instructor {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    /** Set for internal instructors; null → external trainer. */
    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "external_name", length = 200)
    private String externalName;

    @Column(length = 200)
    private String email;

    @Column(length = 500)
    private String qualifications;

    /** Feeds M407 cost tracking. */
    @Column(name = "hourly_cost", precision = 12, scale = 2)
    private BigDecimal hourlyCost;

    /** Aggregated from M408 feedback. */
    @Column(precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
