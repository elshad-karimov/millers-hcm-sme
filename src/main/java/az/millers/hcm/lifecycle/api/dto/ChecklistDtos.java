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
            Integer dueOffsetDays,
            Boolean required) {}

    public record TemplateRequest(
            String code,
            String name,
            String description,
            ChecklistFlowType flowType,
            Boolean active,
            List<TemplateTaskRequest> tasks) {}

    public record TemplateTaskResponse(
            UUID id,
            int stepOrder,
            String title,
            String description,
            String defaultOwnerRole,
            Integer dueOffsetDays,
            boolean required) {}

    public record TemplateResponse(
            UUID id,
            String code,
            String name,
            String description,
            ChecklistFlowType flowType,
            boolean active,
            List<TemplateTaskResponse> tasks) {}

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
