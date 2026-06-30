package az.millers.hcm.payroll.api.dto;

import az.millers.hcm.payroll.domain.GLAccountType;

public record GLAccountMappingRequest(
    String componentKind,
    String componentCode,
    GLAccountType accountType,
    String glAccountCode,
    String glAccountName
) {}
