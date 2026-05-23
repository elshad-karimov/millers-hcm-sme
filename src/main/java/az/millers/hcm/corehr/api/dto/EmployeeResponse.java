package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;

public record EmployeeResponse(
        UUID id,
        String employeeNo,
        String firstName,
        String lastName,
        String middleName,
        LocalDate birthDate,
        String gender,
        String nationalId,
        String email,
        String phone,
        LocalDate hireDate,
        EmploymentStatus employmentStatus,
        String departmentName,
        String positionTitle,
        String costCentre,
        UUID orgUnitId,
        UUID positionId,
        UUID managerId,
        /** M37 acting / delegate manager (PRD 9 / 14.9). */
        UUID delegateManagerId,
        LocalDate delegateFrom,
        LocalDate delegateTo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy) {

    public static EmployeeResponse from(Employee e) {
        return new EmployeeResponse(
                e.getId(),
                e.getEmployeeNo(),
                e.getFirstName(),
                e.getLastName(),
                e.getMiddleName(),
                e.getBirthDate(),
                e.getGender(),
                e.getNationalId(),
                e.getEmail(),
                e.getPhone(),
                e.getHireDate(),
                e.getEmploymentStatus(),
                e.getDepartmentName(),
                e.getPositionTitle(),
                e.getCostCentre(),
                e.getOrgUnitId(),
                e.getPositionId(),
                e.getManagerId(),
                e.getDelegateManagerId(),
                e.getDelegateFrom(),
                e.getDelegateTo(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getCreatedBy(),
                e.getUpdatedBy());
    }
}
