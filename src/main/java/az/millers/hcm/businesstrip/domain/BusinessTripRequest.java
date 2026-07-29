package az.millers.hcm.businesstrip.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "business_trip_request", schema = "business_trip")
@Getter
@Setter
@NoArgsConstructor
public class BusinessTripRequest {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "trip_no", nullable = false, unique = true)
    private String tripNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type", nullable = false)
    private TripType tripType;

    @Column(name = "destination_country")
    private String destinationCountry;

    @Column(name = "destination_city", nullable = false)
    private String destinationCity;

    @Column(columnDefinition = "text")
    private String purpose;

    private String project;

    @Column(name = "cost_centre")
    private String costCentre;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "daily_allowance", precision = 12, scale = 2)
    private BigDecimal dailyAllowance;

    @Column(name = "requested_advance", precision = 12, scale = 2)
    private BigDecimal requestedAdvance;

    @Column(name = "approved_advance", precision = 12, scale = 2)
    private BigDecimal approvedAdvance;

    @Column(name = "paid_advance", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAdvance = BigDecimal.ZERO;

    @Column(name = "actual_expense", precision = 12, scale = 2)
    private BigDecimal actualExpense;

    @Column(name = "meals_provided", nullable = false)
    private boolean mealsProvided;

    @Column(name = "accommodation_provided", nullable = false)
    private boolean accommodationProvided;

    @Column(name = "attachment_urls", columnDefinition = "text")
    private String attachmentUrls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (currency == null) currency = "AZN";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
