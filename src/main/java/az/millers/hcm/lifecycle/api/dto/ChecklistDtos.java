package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.lifecycle.domain.ChecklistAssignmentStatus;
import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatusValue;

/** DTOs for M105/M106 onboarding/offboarding checklists. */
public final class ChecklistDtos {
    private ChecklistDtos() {}

    // ── Template ────────────────────────────────────────────────────────────

    public record TemplateTaskRequest(
            int stepOrder,
            String title,
            String description,
            String defaultOwnerRole,
            String taskType,
            String category,
            String trainingTargetKind,
            UUID trainingTargetId,
            Integer dueOffsetDays,
            Boolean required) {}

    public record TemplateRequest(
            String code,
            String name,
            String description,
            ChecklistFlowType flowType,
            Boolean active,
            Integer priority,
            List<TemplateTaskRequest> tasks,
            List<TemplateRuleRequest> rules) {}

    /**
     * One match rule for a template (M298). Every non-null field is an AND-ed
     * filter against the new hire's Employee attributes; all-null = catch-all.
     */
    public record TemplateRuleRequest(
            String departmentName,
            UUID positionId,
            UUID orgUnitId,
            UUID workLocationId,
            String employmentType,
            String employeeCategory,
            String nationality,
            Boolean active) {}

    public record TemplateRuleResponse(
            UUID id,
            String departmentName,
            UUID positionId,
            UUID orgUnitId,
            UUID workLocationId,
            String employmentType,
            String employeeCategory,
            String nationality,
            boolean active,
            int specificity) {}

    public record TemplateTaskResponse(
            UUID id,
            int stepOrder,
            String title,
            String description,
            String defaultOwnerRole,
            String taskType,
            String category,
            String trainingTargetKind,
            UUID trainingTargetId,
            Integer dueOffsetDays,
            boolean required) {}

    public record TemplateResponse(
            UUID id,
            String code,
            String name,
            String description,
            ChecklistFlowType flowType,
            boolean active,
            int priority,
            List<TemplateTaskResponse> tasks,
            List<TemplateRuleResponse> rules) {}

    /**
     * One row of a match preview (M298): how a given template ranks for a
     * given employee. {@code selected} marks the single winning template.
     */
    public record TemplateMatchResult(
            UUID templateId,
            String code,
            String name,
            int priority,
            int specificity,
            boolean isDefault,
            boolean selected) {}

    // ── Assignment ──────────────────────────────────────────────────────────

    public record StartAssignmentRequest(
            UUID templateId,
            UUID employeeId,
            LocalDate anchorDate,
            String notes) {}

    public record UpdateTaskRequest(
            ChecklistTaskStatusValue status,
            String assignedToUsername,
            LocalDate dueDate,
            String notes) {}

    public record TaskStatusResponse(
            UUID id,
            UUID templateTaskId,
            int stepOrder,
            String title,
            String description,
            String ownerRole,
            String taskType,
            String category,
            String trainingTargetKind,
            UUID trainingTargetId,
            String assignedToUsername,
            LocalDate dueDate,
            boolean required,
            ChecklistTaskStatusValue status,
            OffsetDateTime completedAt,
            String completedBy,
            String notes) {}

    public record AssignmentResponse(
            UUID id,
            UUID templateId,
            String templateCode,
            String templateName,
            ChecklistFlowType flowType,
            UUID employeeId,
            String employeeName,
            LocalDate anchorDate,
            ChecklistAssignmentStatus status,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            String startedBy,
            String notes,
            int totalTasks,
            int completedTasks,
            int requiredTotal,
            int requiredCompleted,
            int progressPercent,
            List<TaskStatusResponse> tasks) {}
}
