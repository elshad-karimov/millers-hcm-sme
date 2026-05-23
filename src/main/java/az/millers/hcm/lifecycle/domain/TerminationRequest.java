package az.millers.hcm.lifecycle.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

@Entity
@Table(name = "termination_request", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class TerminationRequest {

    @Id
    private UUID id;

    @Column(name = "termination_no", nullable = false, unique = true)
    private String terminationNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 40)
    private TerminationReason reasonCode;

    @Column(name = "reason_detail", columnDefinition = "text")
    private String reasonDetail;

    @Column(name = "notice_date", nullable = false)
    private LocalDate noticeDate;

    @Column(name = "last_working_date", nullable = false)
    private LocalDate lastWorkingDate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TerminationStatus status;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "unused_leave_days", precision = 9, scale = 2)
    private BigDecimal unusedLeaveDays;

    @Column(name = "unused_leave_payout", precision = 14, scale = 2)
    private BigDecimal unusedLeavePayout;

    @Column(name = "severance_amount", precision = 14, scale = 2)
    private BigDecimal severanceAmount;

    @Column(name = "final_settlement_amount", precision = 14, scale = 2)
    private BigDecimal finalSettlementAmount;

    @Column(length = 3)
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payout_calc_details")
    private Map<String, Object> payoutCalcDetails;

    @Column(name = "clearance_it", nullable = false)
    private boolean clearanceIt;

    @Column(name = "clearance_hr", nullable = false)
    private boolean clearanceHr;

    @Column(name = "clearance_finance", nullable = false)
    private boolean clearanceFinance;

    @Column(name = "clearance_assets", nullable = false)
    private boolean clearanceAssets;

    @Column(name = "attachment_urls", columnDefinition = "text")
    private String attachmentUrls;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "processed_by")
    private String processedBy;

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
