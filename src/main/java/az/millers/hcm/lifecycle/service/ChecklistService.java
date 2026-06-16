package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.AssignmentResponse;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.StartAssignmentRequest;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.TaskStatusResponse;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.TemplateMatchResult;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.TemplateRequest;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.TemplateResponse;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.TemplateRuleRequest;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.TemplateRuleResponse;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.TemplateTaskRequest;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.TemplateTaskResponse;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.UpdateTaskRequest;
import az.millers.hcm.lifecycle.domain.ChecklistAssignment;
import az.millers.hcm.lifecycle.domain.ChecklistAssignmentStatus;
import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.domain.ChecklistOnboardingCategory;
import az.millers.hcm.lifecycle.domain.ChecklistTaskType;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatus;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatusValue;
import az.millers.hcm.lifecycle.domain.ChecklistTemplate;
import az.millers.hcm.lifecycle.domain.ChecklistTemplateRule;
import az.millers.hcm.lifecycle.domain.ChecklistTemplateTask;
import az.millers.hcm.lifecycle.repo.ChecklistAssignmentRepository;
import az.millers.hcm.lifecycle.repo.ChecklistTaskStatusRepository;
import az.millers.hcm.lifecycle.repo.ChecklistTemplateRepository;
import az.millers.hcm.lifecycle.repo.ChecklistTemplateRuleRepository;
import az.millers.hcm.lifecycle.repo.ChecklistTemplateTaskRepository;
import az.millers.hcm.lifecycle.service.ChecklistTemplateMatcher.MatchOutcome;
import az.millers.hcm.security.CurrentRequest;

/**
 * Onboarding / offboarding checklist service (M105/M106).
 *
 * <p>Templates define the canonical task list per flow type; assignments
 * are per-employee instantiations. Per-task progress is tracked on
 * {@link ChecklistTaskStatus} rows copied from the template at start time
 * (so changing a template doesn't retroactively change running assignments).
 *
 * <p>Status auto-advances: assignment is COMPLETED when every required task
 * is DONE or SKIPPED. Non-required tasks don't block completion.
 */
@Service
public class ChecklistService {

    private static final String MODULE = "LIFECYCLE";
    private static final String ENTITY = "ChecklistAssignment";

    private final ChecklistTemplateRepository templates;
    private final ChecklistTemplateTaskRepository templateTasks;
    private final ChecklistTemplateRuleRepository templateRules;
    private final ChecklistAssignmentRepository assignments;
    private final ChecklistTaskStatusRepository taskStatuses;
    private final EmployeeRepository employees;
    private final ChecklistTemplateMatcher matcher;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ChecklistService(ChecklistTemplateRepository templates,
                             ChecklistTemplateTaskRepository templateTasks,
                             ChecklistTemplateRuleRepository templateRules,
                             ChecklistAssignmentRepository assignments,
                             ChecklistTaskStatusRepository taskStatuses,
                             EmployeeRepository employees,
                             ChecklistTemplateMatcher matcher,
                             AuditService audit,
                             CurrentRequest currentRequest) {
        this.templates = templates;
        this.templateTasks = templateTasks;
        this.templateRules = templateRules;
        this.assignments = assignments;
        this.taskStatuses = taskStatuses;
        this.employees = employees;
        this.matcher = matcher;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Templates ───────────────────────────────────────────────────────────

    @Transactional
    public TemplateResponse upsertTemplate(TemplateRequest req) {
        if (req.code() == null || req.code().isBlank()) {
            throw new BadRequestException("Template code is required");
        }
        if (req.flowType() == null) {
            throw new BadRequestException("flowType is required");
        }
        ChecklistTemplate t = templates.findByCode(req.code())
                .orElseGet(ChecklistTemplate::new);
        t.setCode(req.code());
        t.setName(req.name());
        t.setDescription(req.description());
        t.setFlowType(req.flowType());
        if (req.active() != null) t.setActive(req.active());
        if (req.priority() != null) t.setPriority(req.priority());
        templates.save(t);

        // Replace template tasks atomically. Flush the delete before re-inserting
        // so re-saving a template with the same step orders doesn't trip the
        // (template_id, step_order) unique index — Hibernate would otherwise
        // order the inserts ahead of the delete within a single flush.
        templateTasks.deleteByTemplateId(t.getId());
        templateTasks.flush();
        if (req.tasks() != null) {
            for (TemplateTaskRequest tr : req.tasks()) {
                ChecklistTemplateTask task = new ChecklistTemplateTask();
                task.setTemplateId(t.getId());
                task.setStepOrder(tr.stepOrder());
                task.setTitle(tr.title());
                task.setDescription(tr.description());
                task.setDefaultOwnerRole(tr.defaultOwnerRole());
                task.setTaskType(parseTaskType(tr.taskType()));
                task.setCategory(parseCategory(tr.category()));
                task.setDueOffsetDays(tr.dueOffsetDays());
                if (tr.required() != null) task.setRequired(tr.required());
                templateTasks.save(task);
            }
        }

        // Replace match rules atomically (M298). Flush the delete first for the
        // same reason as the tasks above.
        templateRules.deleteByTemplateId(t.getId());
        templateRules.flush();
        if (req.rules() != null) {
            for (TemplateRuleRequest rr : req.rules()) {
                ChecklistTemplateRule rule = new ChecklistTemplateRule();
                rule.setTemplateId(t.getId());
                rule.setDepartmentName(blankToNull(rr.departmentName()));
                rule.setPositionId(rr.positionId());
                rule.setOrgUnitId(rr.orgUnitId());
                rule.setWorkLocationId(rr.workLocationId());
                rule.setEmploymentType(blankToNull(rr.employmentType()));
                rule.setEmployeeCategory(blankToNull(rr.employeeCategory()));
                rule.setNationality(blankToNull(rr.nationality()));
                if (rr.active() != null) rule.setActive(rr.active());
                templateRules.save(rule);
            }
        }
        return getTemplate(t.getId());
    }

    @Transactional(readOnly = true)
    public TemplateResponse getTemplate(UUID id) {
        ChecklistTemplate t = templates.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + id));
        List<TemplateTaskResponse> tasks = templateTasks
                .findByTemplateIdOrderByStepOrderAsc(id).stream()
                .map(task -> new TemplateTaskResponse(
                        task.getId(), task.getStepOrder(), task.getTitle(),
                        task.getDescription(), task.getDefaultOwnerRole(),
                        task.getTaskType().name(),
                        task.getCategory() == null ? null : task.getCategory().name(),
                        task.getDueOffsetDays(), task.isRequired()))
                .toList();
        List<TemplateRuleResponse> rules = templateRules.findByTemplateId(id).stream()
                .map(r -> new TemplateRuleResponse(
                        r.getId(), r.getDepartmentName(), r.getPositionId(),
                        r.getOrgUnitId(), r.getWorkLocationId(), r.getEmploymentType(),
                        r.getEmployeeCategory(), r.getNationality(), r.isActive(),
                        r.specificity()))
                .toList();
        return new TemplateResponse(t.getId(), t.getCode(), t.getName(),
                t.getDescription(), t.getFlowType(), t.isActive(), t.getPriority(),
                tasks, rules);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> templatesByFlow(ChecklistFlowType flow) {
        return templates.findByFlowTypeAndActiveTrueOrderByName(flow).stream()
                .map(t -> getTemplate(t.getId()))
                .toList();
    }

    // ── Assignments ─────────────────────────────────────────────────────────

    @Transactional
    public AssignmentResponse start(StartAssignmentRequest req) {
        ChecklistTemplate tpl = templates.findById(req.templateId())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + req.templateId()));
        if (!tpl.isActive()) {
            throw new BadRequestException("Cannot start an inactive template");
        }

        // Guard duplicate active assignment (matches partial unique index).
        assignments.findByEmployeeIdAndFlowTypeAndStatus(
                        req.employeeId(), tpl.getFlowType(), ChecklistAssignmentStatus.IN_PROGRESS)
                .ifPresent(existing -> {
                    throw new BadRequestException(
                            "Employee already has an active " + tpl.getFlowType()
                                    + " checklist. Complete or cancel it first.");
                });

        ChecklistAssignment a = new ChecklistAssignment();
        a.setTemplateId(req.templateId());
        a.setEmployeeId(req.employeeId());
        a.setFlowType(tpl.getFlowType());
        a.setAnchorDate(req.anchorDate() != null ? req.anchorDate() : LocalDate.now());
        a.setNotes(req.notes());
        a.setStartedBy(currentRequest.username());
        assignments.save(a);

        // Snapshot every template task into a task-status row.
        List<ChecklistTemplateTask> tplTasks =
                templateTasks.findByTemplateIdOrderByStepOrderAsc(req.templateId());
        for (ChecklistTemplateTask tt : tplTasks) {
            ChecklistTaskStatus s = new ChecklistTaskStatus();
            s.setAssignmentId(a.getId());
            s.setTemplateTaskId(tt.getId());
            s.setStepOrder(tt.getStepOrder());
            s.setTitle(tt.getTitle());
            s.setDescription(tt.getDescription());
            s.setTaskType(tt.getTaskType());
            s.setCategory(tt.getCategory());
            // Owner defaults to the task type's natural owner when the template
            // didn't set one explicitly (M299), so typed tasks route to the
            // right persona's dashboard out of the box.
            s.setOwnerRole(tt.getDefaultOwnerRole() != null
                    ? tt.getDefaultOwnerRole()
                    : tt.getTaskType().defaultOwnerRole());
            s.setRequired(tt.isRequired());
            if (tt.getDueOffsetDays() != null) {
                s.setDueDate(a.getAnchorDate().plusDays(tt.getDueOffsetDays()));
            }
            taskStatuses.save(s);
        }

        audit.record(MODULE, ENTITY, a.getId().toString(), "START", null,
                Map.of("templateId", req.templateId().toString(),
                        "employeeId", req.employeeId().toString(),
                        "flow", tpl.getFlowType().name(),
                        "taskCount", tplTasks.size()));
        return toResponse(a, tpl);
    }

    /**
     * Start the single best-matching template for an employee + flow (M298).
     *
     * <p>Used by the hire pipeline to auto-start onboarding: the
     * {@link ChecklistTemplateMatcher} picks the template whose rules fit the
     * new hire most specifically. Idempotent — returns empty (does nothing) if
     * the employee already has an active checklist for this flow, or if no
     * template matches at all.
     */
    @Transactional
    public Optional<AssignmentResponse> startMatched(UUID employeeId, ChecklistFlowType flow,
                                                     LocalDate anchorDate, String notes) {
        Employee emp = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        if (assignments.findByEmployeeIdAndFlowTypeAndStatus(
                employeeId, flow, ChecklistAssignmentStatus.IN_PROGRESS).isPresent()) {
            return Optional.empty();
        }
        return matcher.bestMatch(emp, flow)
                .map(tpl -> start(new StartAssignmentRequest(
                        tpl.getId(), employeeId, anchorDate, notes)));
    }

    /**
     * Preview how each active template ranks for an employee + flow (M298),
     * best fit first with the winner flagged. Lets HR see which onboarding
     * template a hire would auto-receive before the hire happens.
     */
    @Transactional(readOnly = true)
    public List<TemplateMatchResult> matchPreview(UUID employeeId, ChecklistFlowType flow) {
        Employee emp = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        List<MatchOutcome> ranked = matcher.rank(emp, flow);
        List<TemplateMatchResult> out = new ArrayList<>(ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            MatchOutcome m = ranked.get(i);
            out.add(new TemplateMatchResult(
                    m.template().getId(), m.template().getCode(), m.template().getName(),
                    m.template().getPriority(), m.specificity(), m.isDefault(), i == 0));
        }
        return out;
    }

    @Transactional
    public AssignmentResponse updateTask(UUID taskStatusId, UpdateTaskRequest req) {
        ChecklistTaskStatus s = taskStatuses.findById(taskStatusId)
                .orElseThrow(() -> new ResourceNotFoundException("Task status not found: " + taskStatusId));
        ChecklistAssignment a = assignments.findById(s.getAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        if (a.getStatus() != ChecklistAssignmentStatus.IN_PROGRESS) {
            throw new BadRequestException("Cannot update tasks on a "
                    + a.getStatus().name() + " checklist");
        }
        boolean reachedDone = req.status() != null
                && (req.status() == ChecklistTaskStatusValue.DONE
                        || req.status() == ChecklistTaskStatusValue.SKIPPED)
                && s.getStatus() != req.status();
        if (req.status() != null) s.setStatus(req.status());
        if (req.assignedToUsername() != null) s.setAssignedToUsername(req.assignedToUsername());
        if (req.dueDate() != null) s.setDueDate(req.dueDate());
        if (req.notes() != null) s.setNotes(req.notes());
        if (reachedDone) {
            s.setCompletedAt(OffsetDateTime.now());
            s.setCompletedBy(currentRequest.username());
        } else if (req.status() != null
                && req.status() != ChecklistTaskStatusValue.DONE
                && req.status() != ChecklistTaskStatusValue.SKIPPED) {
            // Reverted away from a terminal state — clear the completed marker.
            s.setCompletedAt(null);
            s.setCompletedBy(null);
        }
        taskStatuses.save(s);
        autoCompleteIfDone(a);
        ChecklistTemplate tpl = templates.findById(a.getTemplateId()).orElse(null);
        return toResponse(a, tpl);
    }

    @Transactional
    public AssignmentResponse cancel(UUID assignmentId, String reason) {
        ChecklistAssignment a = mustFind(assignmentId);
        if (a.getStatus() != ChecklistAssignmentStatus.IN_PROGRESS) {
            throw new BadRequestException("Only IN_PROGRESS checklists can be cancelled");
        }
        a.setStatus(ChecklistAssignmentStatus.CANCELLED);
        a.setCompletedAt(OffsetDateTime.now());
        if (reason != null && !reason.isBlank()) {
            a.setNotes(a.getNotes() == null
                    ? "Cancelled: " + reason
                    : a.getNotes() + "\nCancelled: " + reason);
        }
        assignments.save(a);
        audit.record(MODULE, ENTITY, assignmentId.toString(), "CANCEL", null,
                Map.of("reason", reason == null ? "" : reason));
        ChecklistTemplate tpl = templates.findById(a.getTemplateId()).orElse(null);
        return toResponse(a, tpl);
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getAssignment(UUID id) {
        return toResponse(mustFind(id));
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> forEmployee(UUID employeeId) {
        return assignments.findByEmployeeIdOrderByStartedAtDesc(employeeId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> activeByFlow(ChecklistFlowType flow) {
        return assignments
                .findByStatusAndFlowTypeOrderByStartedAtDesc(
                        ChecklistAssignmentStatus.IN_PROGRESS, flow).stream()
                .map(this::toResponse).toList();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void autoCompleteIfDone(ChecklistAssignment a) {
        List<ChecklistTaskStatus> tasks =
                taskStatuses.findByAssignmentIdOrderByStepOrderAsc(a.getId());
        int requiredTotal = 0;
        int requiredDone = 0;
        for (ChecklistTaskStatus t : tasks) {
            if (!t.isRequired()) continue;
            requiredTotal++;
            if (t.getStatus() == ChecklistTaskStatusValue.DONE
                    || t.getStatus() == ChecklistTaskStatusValue.SKIPPED) {
                requiredDone++;
            }
        }
        if (requiredTotal > 0 && requiredDone == requiredTotal
                && a.getStatus() == ChecklistAssignmentStatus.IN_PROGRESS) {
            a.setStatus(ChecklistAssignmentStatus.COMPLETED);
            a.setCompletedAt(OffsetDateTime.now());
            assignments.save(a);
            audit.record(MODULE, ENTITY, a.getId().toString(), "AUTO_COMPLETE",
                    null, Map.of("requiredTotal", requiredTotal));
        }
    }

    /** Package-private for tests: progress percent over total tasks (DONE+SKIPPED). */
    static int progressPercent(List<ChecklistTaskStatus> tasks) {
        if (tasks == null || tasks.isEmpty()) return 0;
        long done = tasks.stream()
                .filter(t -> t.getStatus() == ChecklistTaskStatusValue.DONE
                        || t.getStatus() == ChecklistTaskStatusValue.SKIPPED)
                .count();
        return (int) Math.round(done * 100.0 / tasks.size());
    }

    private AssignmentResponse toResponse(ChecklistAssignment a) {
        ChecklistTemplate tpl = templates.findById(a.getTemplateId()).orElse(null);
        return toResponse(a, tpl);
    }

    private AssignmentResponse toResponse(ChecklistAssignment a, ChecklistTemplate tpl) {
        List<ChecklistTaskStatus> tasks =
                taskStatuses.findByAssignmentIdOrderByStepOrderAsc(a.getId());
        int total = tasks.size();
        int completed = 0;
        int requiredTotal = 0;
        int requiredCompleted = 0;
        List<TaskStatusResponse> taskDtos = new ArrayList<>(total);
        for (ChecklistTaskStatus t : tasks) {
            boolean isDone = t.getStatus() == ChecklistTaskStatusValue.DONE
                    || t.getStatus() == ChecklistTaskStatusValue.SKIPPED;
            if (isDone) completed++;
            if (t.isRequired()) {
                requiredTotal++;
                if (isDone) requiredCompleted++;
            }
            taskDtos.add(new TaskStatusResponse(
                    t.getId(), t.getTemplateTaskId(), t.getStepOrder(), t.getTitle(),
                    t.getDescription(), t.getOwnerRole(),
                    t.getTaskType().name(),
                    t.getCategory() == null ? null : t.getCategory().name(),
                    t.getAssignedToUsername(),
                    t.getDueDate(), t.isRequired(), t.getStatus(),
                    t.getCompletedAt(), t.getCompletedBy(), t.getNotes()));
        }

        Optional<Employee> emp = employees.findById(a.getEmployeeId());
        String empName = emp.map(e ->
                ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                        + (e.getLastName() == null ? "" : e.getLastName())).trim())
                .orElse(null);

        return new AssignmentResponse(
                a.getId(), a.getTemplateId(),
                tpl == null ? null : tpl.getCode(),
                tpl == null ? null : tpl.getName(),
                a.getFlowType(),
                a.getEmployeeId(), empName,
                a.getAnchorDate(), a.getStatus(),
                a.getStartedAt(), a.getCompletedAt(),
                a.getStartedBy(), a.getNotes(),
                total, completed, requiredTotal, requiredCompleted,
                progressPercent(tasks), taskDtos);
    }

    private ChecklistAssignment mustFind(UUID id) {
        return assignments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + id));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static ChecklistTaskType parseTaskType(String s) {
        if (s == null || s.isBlank()) return ChecklistTaskType.MANUAL_TASK;
        try {
            return ChecklistTaskType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown task type: " + s);
        }
    }

    private static ChecklistOnboardingCategory parseCategory(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return ChecklistOnboardingCategory.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown onboarding category: " + s);
        }
    }
}
