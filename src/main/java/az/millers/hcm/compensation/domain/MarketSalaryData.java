package az.millers.hcm.compensation.domain;

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

@Entity
@Table(name = "market_salary_data", schema = "compensation")
@Getter
@Setter
@NoArgsConstructor
public class MarketSalaryData {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "survey_id", nullable = false)
    private UUID surveyId;

    @Column(name = "job_code", length = 60)
    private String jobCode;

    @Column(name = "grade_code", length = 60)
    private String gradeCode;

    @Column(length = 80)
    private String location;

    @Column(precision = 14, scale = 2)
    private BigDecimal p25;

    @Column(precision = 14, scale = 2)
    private BigDecimal p50;

    @Column(precision = 14, scale = 2)
    private BigDecimal p75;

    @Column(precision = 14, scale = 2)
    private BigDecimal p90;

    @Column(length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (currency == null) currency = "AZN";
    }
}
