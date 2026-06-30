package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record YtdSummaryResponse(
        int year,
        List<EmployeeYtd> employees) {

    public record EmployeeYtd(
            UUID employeeId,
            String employeeNo,
            String name,
            BigDecimal totalGross,
            BigDecimal totalIncomeTax,
            BigDecimal totalDsmf,
            BigDecimal totalMmi,
            BigDecimal totalUnemployment,
            BigDecimal totalBonuses,
            BigDecimal totalNet,
            int monthsCount) {
    }
}
