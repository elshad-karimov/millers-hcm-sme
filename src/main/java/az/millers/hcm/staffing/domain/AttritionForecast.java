package az.millers.hcm.staffing.domain;

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

/**
 * M423: Attrition forecast row.
 */
@Entity
@Table(name = "attrition_forecast", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class AttritionForecast {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    @Column(name = "forecast_date", nullable = false)
    private LocalDate forecastDate;

    @Column(name = "expected_exits", nullable = false, precision = 6, scale = 2)
    private BigDecimal expectedExits = BigDecimal.ZERO;

    @Column(nullable = false, length = 50)
    private String basis;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
