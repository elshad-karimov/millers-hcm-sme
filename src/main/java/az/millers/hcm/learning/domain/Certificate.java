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

@Entity
@Table(name = "certificate", schema = "learning")
@Getter
@Setter
@NoArgsConstructor
public class Certificate {

    @Id
    private UUID id;

    @Column(name = "certificate_no", nullable = false, unique = true)
    private String certificateNo;

    @Column(name = "enrollment_id", nullable = false, unique = true)
    private UUID enrollmentId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "score_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal scorePercent;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by")
    private String revokedBy;

    @Column(name = "revoked_reason", columnDefinition = "text")
    private String revokedReason;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (issuedAt == null) issuedAt = OffsetDateTime.now();
    }
}
