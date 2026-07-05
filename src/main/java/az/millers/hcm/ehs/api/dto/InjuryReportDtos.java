package az.millers.hcm.ehs.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.ehs.domain.IncidentSeverity;
import az.millers.hcm.ehs.domain.InjuryReport;

public class InjuryReportDtos {

    public record CreateInjuryReportRequest(
            UUID incidentId,
            UUID employeeId,
            String injuryType,
            String bodyPart,
            IncidentSeverity severity,
            Boolean medicalTreatment,
            Boolean firstAid,
            Boolean hospital,
            Integer lostTimeDays,
            Boolean restrictedDuty,
            String insuranceClaimRef,
            String notes
    ) {}

    public record UpdateInjuryReportRequest(
            String injuryType,
            String bodyPart,
            IncidentSeverity severity,
            Boolean medicalTreatment,
            Boolean firstAid,
            Boolean hospital,
            Integer lostTimeDays,
            Boolean restrictedDuty,
            String insuranceClaimRef,
            String notes
    ) {}

    public record InjuryReportResponse(
            UUID id,
            String tenantId,
            UUID incidentId,
            UUID employeeId,
            String employeeName,
            String injuryType,
            String bodyPart,
            IncidentSeverity severity,
            Boolean medicalTreatment,
            Boolean firstAid,
            Boolean hospital,
            Integer lostTimeDays,
            Boolean restrictedDuty,
            String insuranceClaimRef,
            String notes,
            String createdBy,
            OffsetDateTime createdAt,
            String updatedBy,
            OffsetDateTime updatedAt
    ) {
        public static InjuryReportResponse from(InjuryReport report, String employeeName) {
            return new InjuryReportResponse(
                    report.getId(),
                    report.getTenantId(),
                    report.getIncidentId(),
                    report.getEmployeeId(),
                    employeeName,
                    report.getInjuryType(),
                    report.getBodyPart(),
                    report.getSeverity(),
                    report.getMedicalTreatment(),
                    report.getFirstAid(),
                    report.getHospital(),
                    report.getLostTimeDays(),
                    report.getRestrictedDuty(),
                    report.getInsuranceClaimRef(),
                    report.getNotes(),
                    report.getCreatedBy(),
                    report.getCreatedAt(),
                    report.getUpdatedBy(),
                    report.getUpdatedAt()
            );
        }
    }
}
