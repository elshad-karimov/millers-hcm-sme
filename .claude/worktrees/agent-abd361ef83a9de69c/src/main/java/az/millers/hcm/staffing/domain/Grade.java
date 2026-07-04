package az.millers.hcm.staffing.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Salary band (M75 / P2-22). Replaces the free-text {@code grade} string on
 * {@code staffing.position} — that column stays for backward compat, but new
 * positions reference {@link #id} via {@code position.grade_id} for proper
 * pay-band enforcement.
 */
@Entity
@Table(name = "grade", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class Grade {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** Numeric rank for sorting / hierarchy queries. */
    @Column
    private Integer level;

    @Column(name = "min_salary", precision = 14, scale = 2)
    private BigDecimal minSalary;

    @Column(name = "max_salary", precision = 14, scale = 2)
    private BigDecimal maxSalary;

    @Column(nullable = false, length = 3)
    private String currency = "AZN";

    @Column(nullable = false)
    private boolean active = true;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
