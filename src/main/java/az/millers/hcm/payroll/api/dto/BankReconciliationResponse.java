package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BankReconciliationResponse(
        UUID runId,
        int periodYear,
        int periodMonth,
        BigDecimal payrollNetTotal,
        BigDecimal bankFileTotal,
        BigDecimal delta,
        boolean balanced,
        boolean bankFileExists
) {}
