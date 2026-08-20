package az.millers.hcm.payroll.profile;

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

/**
 * One window of a balancing scheme — Jan–Apr settled in April, and so on.
 *
 * <p>Fixed company-wide calendar dates, deliberately not "four months from the
 * employee's start date": the company states 30 April, 31 August and
 * 31 December as the settlement dates for everyone.
 */
@Entity
@Table(name = "balancing_period_def", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class BalancingPeriodDef {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "scheme_code", nullable = false, length = 40)
    private String schemeCode;

    @Column(name = "period_seq", nullable = false)
    private int periodSeq;

    @Column(name = "start_month", nullable = false)
    private int startMonth;

    @Column(name = "end_month", nullable = false)
    private int endMonth;

    @Column(name = "settlement_month", nullable = false)
    private int settlementMonth;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }

    public boolean covers(int month) {
        return month >= startMonth && month <= endMonth;
    }

    public LocalDate startOn(int year) {
        return LocalDate.of(year, startMonth, 1);
    }

    public LocalDate endOn(int year) {
        return LocalDate.of(year, endMonth, 1).withDayOfMonth(
                LocalDate.of(year, endMonth, 1).lengthOfMonth());
    }

    /** True when this period's excess becomes payable in the given month. */
    public boolean settlesIn(int month) {
        return settlementMonth == month;
    }
}
