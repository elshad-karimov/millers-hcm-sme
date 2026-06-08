package az.millers.hcm.corehr.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.domain.EmploymentType;
import az.millers.hcm.corehr.domain.MaritalStatus;

public record EmployeeResponse(
        UUID id,
        String employeeNo,
        String firstName,
        String lastName,
        String middleName,
        LocalDate birthDate,
        String gender,
        MaritalStatus maritalStatus,
        String nationality,
        String nationalId,
        String email,
        String phone,
        LocalDate hireDate,
        EmploymentStatus employmentStatus,
        EmploymentType employmentType,
        BigDecimal ftePercent,
        String departmentName,
        String positionTitle,
        String costCentre,
        UUID orgUnitId,
        UUID positionId,
        UUID managerId,
        /** M141 — primary work location. */
        UUID workLocationId,
        /** M37 acting / delegate manager (PRD 9 / 14.9). */
        UUID delegateManagerId,
        LocalDate delegateFrom,
        LocalDate delegateTo,
        /** M66 / P1-08. Null = falls through to the default leave group. */
        UUID leaveGroupId,
        /** M75 / P2-19. Null = falls through to the default payroll group. */
        UUID payrollGroupId,
        /** M75 / P2-21. Dotted-line manager. */
        UUID matrixManagerId,
        /** M146 / §9. Functional manager. */
        UUID functionalManagerId,
        /** M78 / P2-15. Rehire-eligible flag — defaults to true. */
        boolean rehireEligible,
        /** M78 / P2-15. Soft self-FK when this row was created via the rehire flow. */
        UUID previousEmployeeId,
        /** M78 / P2-15. Captured at rehire time. */
        String rehireReason,
        /** M132 — cosmetic Section 1 fields. */
        String preferredName,
        String placeOfBirth,
        String bloodGroup,
        String religion,
        String nativeLanguage,
        /** M133 — Section 3 contact fields. */
        String altPhone,
        String workEmail,
        String workPhone,
        String extension,
        String deskNumber,
        /** M134 — Section 4 employment fields. */
        String employeeCategory,
        LocalDate seniorityDate,
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
                e.getMaritalStatus(),
                e.getNationality(),
                e.getNationalId(),
                e.getEmail(),
                e.getPhone(),
                e.getHireDate(),
                e.getEmploymentStatus(),
                e.getEmploymentType(),
                e.getFtePercent(),
                e.getDepartmentName(),
                e.getPositionTitle(),
                e.getCostCentre(),
                e.getOrgUnitId(),
                e.getPositionId(),
                e.getManagerId(),
                e.getWorkLocationId(),
                e.getDelegateManagerId(),
                e.getDelegateFrom(),
                e.getDelegateTo(),
                e.getLeaveGroupId(),
                e.getPayrollGroupId(),
                e.getMatrixManagerId(),
                e.getFunctionalManagerId(),
                e.isRehireEligible(),
                e.getPreviousEmployeeId(),
                e.getRehireReason(),
                e.getPreferredName(),
                e.getPlaceOfBirth(),
                e.getBloodGroup(),
                e.getReligion(),
                e.getNativeLanguage(),
                e.getAltPhone(),
                e.getWorkEmail(),
                e.getWorkPhone(),
                e.getExtension(),
                e.getDeskNumber(),
                e.getEmployeeCategory(),
                e.getSeniorityDate(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getCreatedBy(),
                e.getUpdatedBy());
    }
}
