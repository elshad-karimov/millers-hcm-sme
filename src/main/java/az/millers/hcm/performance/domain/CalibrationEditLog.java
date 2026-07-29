package az.millers.hcm.performance.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * M121 — one append-only row per
 * {@link az.millers.hcm.performance.service.CalibrationSessionService#calibrateReview}
 * call inside an IN_PROGRESS session.
 *
 * <p>{@code beforeJson} / {@code afterJson} capture the review fields the
 * calibrate operation touches: managerRating, finalRating, finalBand,
 * recommendation, bonusPercent, calibrationNotes, potentialRating,
 * potentialNotes. Stored as raw {@code Map} so future fields don't need
 * schema work to surface.
 */
@Entity
@Table(name = "calibration_edit_log", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class CalibrationEditLog {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "edited_by", nullable = false, length = 160)
    private String editedBy;

    @Column(name = "edited_at", nullable = false, updatable = false)
    private OffsetDateTime editedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json")
    private java.util.Map<String, Object> beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json")
    private java.util.Map<String, Object> afterJson;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (editedAt == null) editedAt = OffsetDateTime.now();
    }
}
