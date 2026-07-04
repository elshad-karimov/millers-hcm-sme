package az.millers.hcm.budgeting.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import az.millers.hcm.budgeting.domain.DepartmentBudget;
import az.millers.hcm.budgeting.domain.DepartmentBudgetStatus;

public record DepartmentBudgetResponse(
        UUID id,
        UUID cycleId,
        UUID orgUnitId,
        BigDecimal salaryBudget,
        Integer headcountBudget,
        BigDecimal benefitsBudget,
        BigDecimal trainingBudget,
        BigDecimal recruitmentBudget,
        BigDecimal overtimeBudget,
        BigDecimal totalBudget,
        BigDecimal consumedAmount,
        DepartmentBudgetStatus status,
        UUID workflowInstanceId,
        String approvedBy
) {
    public static DepartmentBudgetResponse from(DepartmentBudget b) {
        return new DepartmentBudgetResponse(
                b.getId(),
                b.getCycleId(),
                b.getOrgUnitId(),
                b.getSalaryBudget(),
                b.getHeadcountBudget(),
                b.getBenefitsBudget(),
                b.getTrainingBudget(),
                b.getRecruitmentBudget(),
                b.getOvertimeBudget(),
                b.getTotalBudget(),
                b.getConsumedAmount(),
                b.getStatus(),
                b.getWorkflowInstanceId(),
                b.getApprovedBy()
        );
    }
}
