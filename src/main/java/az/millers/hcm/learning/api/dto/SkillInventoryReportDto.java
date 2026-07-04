package az.millers.hcm.learning.api.dto;

public class SkillInventoryReportDto {

    public record ByDepartmentRow(
            String department,
            String competencyName,
            int employeeCount,
            double avgLevel
    ) {}

    public record CriticalSkillRow(
            String competencyName,
            int requiredLevel,
            int coveredEmployees
    ) {}

    public record CertificationRow(
            String certificationName,
            int totalCount,
            int expiredCount,
            int expiringSoonCount
    ) {}
}
