package az.millers.hcm.payroll.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.payroll.domain.BankFileFormat;
import az.millers.hcm.payroll.domain.PayCycle;
import az.millers.hcm.payroll.domain.PayrollGroup;

public record PayrollGroupResponse(
        UUID id, String code, String name, String description,
        PayCycle payCycle, BankFileFormat bankFileFormat,
        String defaultCurrency, Object rulesJson,
        boolean active, boolean defaultGroup,
        OffsetDateTime createdAt, String createdBy,
        OffsetDateTime updatedAt, String updatedBy) {

    public static PayrollGroupResponse from(PayrollGroup g) {
        return new PayrollGroupResponse(
                g.getId(), g.getCode(), g.getName(), g.getDescription(),
                g.getPayCycle(), g.getBankFileFormat(),
                g.getDefaultCurrency(), g.getRulesJson(),
                g.isActive(), g.isDefaultGroup(),
                g.getCreatedAt(), g.getCreatedBy(),
                g.getUpdatedAt(), g.getUpdatedBy());
    }
}
