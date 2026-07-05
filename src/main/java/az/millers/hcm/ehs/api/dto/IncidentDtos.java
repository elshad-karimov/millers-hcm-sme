package az.millers.hcm.ehs.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.ehs.domain.*;

public class IncidentDtos {

    public record ReportIncidentRequest(
            LocalDate incidentDate,
            LocalTime incidentTime,
            UUID workLocationId,
            UUID orgUnitId,
            IncidentType incidentType,
            IncidentSeverity severity,
            String description,
            String immediateAction,
            List<UUID> involvedEmployeeIds,
            List<WitnessDto> witnesses
    ) {}

    public record UpdateIncidentRequest(
            String description,
            String immediateAction,
            IncidentStatus status,
            List<UUID> involvedEmployeeIds,
            List<WitnessDto> witnesses
    ) {}

    public record CloseIncidentRequest(
            String resolution
    ) {}

    public record IncidentResponse(
            UUID id,
            String tenantId,
            String incidentNo,
            LocalDate incidentDate,
            LocalTime incidentTime,
            UUID workLocationId,
            UUID orgUnitId,
            IncidentType incidentType,
            IncidentSeverity severity,
            UUID reportedByEmployeeId,
            String reportedByEmployeeName,
            String description,
            String immediateAction,
            Boolean investigationRequired,
            IncidentStatus status,
            OffsetDateTime closedAt,
            String createdBy,
            OffsetDateTime createdAt,
            String updatedBy,
            OffsetDateTime updatedAt
    ) {
        public static IncidentResponse from(Incident incident, String reporterName) {
            return new IncidentResponse(
                    incident.getId(),
                    incident.getTenantId(),
                    incident.getIncidentNo(),
                    incident.getIncidentDate(),
                    incident.getIncidentTime(),
                    incident.getWorkLocationId(),
                    incident.getOrgUnitId(),
                    incident.getIncidentType(),
                    incident.getSeverity(),
                    incident.getReportedByEmployeeId(),
                    reporterName,
                    incident.getDescription(),
                    incident.getImmediateAction(),
                    incident.getInvestigationRequired(),
                    incident.getStatus(),
                    incident.getClosedAt(),
                    incident.getCreatedBy(),
                    incident.getCreatedAt(),
                    incident.getUpdatedBy(),
                    incident.getUpdatedAt()
            );
        }
    }

    public record IncidentInvolvedResponse(
            UUID id,
            UUID incidentId,
            UUID employeeId,
            String employeeName
    ) {}

    public record WitnessDto(
            String name,
            String contact,
            String statement
    ) {}

    public record IncidentWitnessResponse(
            UUID id,
            UUID incidentId,
            String name,
            String contact,
            String statement
    ) {
        public static IncidentWitnessResponse from(IncidentWitness w) {
            return new IncidentWitnessResponse(
                    w.getId(),
                    w.getIncidentId(),
                    w.getName(),
                    w.getContact(),
                    w.getStatement()
            );
        }
    }
}
