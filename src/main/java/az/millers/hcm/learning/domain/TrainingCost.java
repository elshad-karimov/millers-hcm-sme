package az.millers.hcm.learning.domain;

import java.math.BigDecimal;
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

/** HCM_14 M407 — typed training cost line per course/session (PRD 14 §19). */
@Entity
@Table(name = "training_cost", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class TrainingCost {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "session_id")
    private UUID sessionId;

    /** INSTRUCTOR | VENUE | MATERIAL | TRAVEL | CATERING | OTHER */
    @Column(name = "cost_type", nullable = false, length = 20)
    private String costType = "OTHER";

    @Column(length = 300)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "AZN";

    @Column(name = "incurred_on")
    private LocalDate incurredOn;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
