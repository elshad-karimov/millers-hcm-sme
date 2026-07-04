package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "erp_export_line", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class ErpExportLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "export_id", nullable = false)
    private ErpExport export;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "account_code", nullable = false)
    private String accountCode;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "cost_centre")
    private String costCentre;

    @Column(name = "description")
    private String description;

    @Column(name = "debit", precision = 18, scale = 2)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(name = "credit", precision = 18, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(name = "currency", length = 3)
    private String currency = "AZN";

    @Column(name = "employee_count")
    private int employeeCount;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
