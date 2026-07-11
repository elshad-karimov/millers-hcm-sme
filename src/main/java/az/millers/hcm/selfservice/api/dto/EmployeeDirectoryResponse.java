package az.millers.hcm.selfservice.api.dto;

import java.util.UUID;

/**
 * M504 — Employee directory response (PUBLIC fields only, no salary/PII).
 */
public record EmployeeDirectoryResponse(
        UUID id,
        String fullName,
        String departmentName,
        String orgUnitName,
        String positionTitle,
        String workEmail,
        String workPhone,
        UUID photoAttachmentId
) {
}
