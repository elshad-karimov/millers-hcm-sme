package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.payroll.domain.AnnualTaxCertificate;
import az.millers.hcm.payroll.domain.CertificateStatus;

public record AnnualTaxCertificateResponse(
        UUID id,
        UUID employeeId,
        String employeeNo,
        String employeeName,
        String maskedNationalId,
        int year,
        BigDecimal annualGross,
        BigDecimal exemptAmount,
        BigDecimal taxableGross,
        BigDecimal totalTaxWithheld,
        CertificateStatus status,
        OffsetDateTime generatedAt,
        String generatedBy) {

    public static AnnualTaxCertificateResponse from(AnnualTaxCertificate cert,
                                                      String employeeNo,
                                                      String employeeName,
                                                      String maskedNationalId) {
        return new AnnualTaxCertificateResponse(
                cert.getId(),
                cert.getEmployeeId(),
                employeeNo,
                employeeName,
                maskedNationalId,
                cert.getYear(),
                cert.getAnnualGross(),
                cert.getExemptAmount(),
                cert.getTaxableGross(),
                cert.getTotalTaxWithheld(),
                cert.getStatus(),
                cert.getGeneratedAt(),
                cert.getGeneratedBy()
        );
    }
}
