package az.millers.hcm.lifecycle.service;

import java.time.OffsetDateTime;
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
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.UpdateTaskRequest;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.BuddyResponse;
import az.millers.hcm.lifecycle.domain.BuddyRole;
import az.millers.hcm.lifecycle.domain.BuddyStatus;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatus;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatusValue;
import az.millers.hcm.lifecycle.domain.OnboardingBuddy;
import az.millers.hcm.lifecycle.repo.ChecklistTaskStatusRepository;
import az.millers.hcm.lifecycle.repo.OnboardingBuddyRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Buddy / mentor assignment for onboarding (M305 — Phase C.1). Pairs a new hire
 * with a peer buddy or a mentor; assigning closes the originating BUDDY_TASK.
 */
@Service
public class OnboardingBuddyService {

    private static final String MODULE = "LIFECYCLE";
    private static final String ENTITY = "OnboardingBuddy";

    private final OnboardingBuddyRepository buddies;
    private final ChecklistTaskStatusRepository taskStatuses;
    private final ChecklistService checklistService;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public OnboardingBuddyService(OnboardingBuddyRepository buddies,
                                  ChecklistTaskStatusRepository taskStatuses,
                                  ChecklistService checklistService,
                                  EmployeeRepository employees,
                                  AuditService audit,
                                  CurrentRequest currentRequest) {
        this.buddies = buddies;
        this.taskStatuses = taskStatuses;
        this.checklistService = checklistService;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public BuddyResponse assign(UUID employeeId, UUID buddyEmployeeId, String roleStr,
                                UUID taskStatusId, String notes) {
        if (employeeId == null || buddyEmployeeId == null) {
            throw new BadRequestException("employeeId and buddyEmployeeId are required");
        }
        if (employeeId.equals(buddyEmployeeId)) {
            throw new BadRequestException("An employee cannot be their own buddy");
        }
        Employee hire = employees.findById(employeeId)
                .orElseThrow(() -> new BadRequestException("Employee not found: " + employeeId));
        Employee buddy = employees.findById(buddyEmployeeId)
                .orElseThrow(() -> new BadRequestException("Buddy not found: " + buddyEmployeeId));
        if (buddy.getEmploymentStatus() == EmploymentStatus.TERMINATED
                || buddy.getEmploymentStatus() == EmploymentStatus.RETIRED) {
            throw new BadRequestException("Buddy must be an active employee");
        }
        BuddyRole role = parseRole(roleStr);
        buddies.findByEmployeeIdAndRoleAndStatus(employeeId, role, BuddyStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new BadRequestException(
                            "This hire already has an active " + role + ". End it first.");
                });

        UUID assignmentId = null;
        if (taskStatusId != null) {
            ChecklistTaskStatus ts = taskStatuses.findById(taskStatusId)
                    .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskStatusId));
            assignmentId = ts.getAssignmentId();
        }

        OnboardingBuddy b = new OnboardingBuddy();
        b.setEmployeeId(employeeId);
        b.setBuddyEmployeeId(buddyEmployeeId);
        b.setAssignmentId(assignmentId);
        b.setTaskStatusId(taskStatusId);
        b.setRole(role);
        b.setStatus(BuddyStatus.ACTIVE);
        b.setNotes(blankToNull(notes));
        b.setAssignedBy(currentRequest.username());
        buddies.save(b);

        if (taskStatusId != null) {
            checklistService.updateTask(taskStatusId, new UpdateTaskRequest(
                    ChecklistTaskStatusValue.DONE, null, null,
                    role + " assigned: " + name(buddy)));
        }
        audit.record(MODULE, ENTITY, b.getId().toString(), "ASSIGN", null,
                Map.of("employeeId", employeeId.toString(),
                        "buddyEmployeeId", buddyEmployeeId.toString(), "role", role.name()));
        return toResponse(b);
    }

    @Transactional
    public BuddyResponse end(UUID id, String reason) {
        OnboardingBuddy b = buddies.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Buddy assignment not found: " + id));
        if (b.getStatus() != BuddyStatus.ACTIVE) {
            throw new BadRequestException("Only an ACTIVE pairing can be ended");
        }
        b.setStatus(BuddyStatus.ENDED);
        b.setEndedAt(OffsetDateTime.now());
        if (reason != null && !reason.isBlank()) {
            b.setNotes(b.getNotes() == null ? "Ended: " + reason : b.getNotes() + "\nEnded: " + reason);
        }
        buddies.save(b);
        audit.record(MODULE, ENTITY, id.toString(), "END", null,
                Map.of("reason", reason == null ? "" : reason));
        return toResponse(b);
    }

    @Transactional(readOnly = true)
    public List<BuddyResponse> forEmployee(UUID employeeId) {
        return buddies.findByEmployeeIdOrderByAssignedAtDesc(employeeId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BuddyResponse> mentees(UUID buddyEmployeeId) {
        return buddies.findByBuddyEmployeeIdAndStatusOrderByAssignedAtDesc(buddyEmployeeId, BuddyStatus.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    private BuddyRole parseRole(String s) {
        if (s == null || s.isBlank()) return BuddyRole.BUDDY;
        try {
            return BuddyRole.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown buddy role: " + s);
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String name(Employee e) {
        return ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                + (e.getLastName() == null ? "" : e.getLastName())).trim();
    }

    private BuddyResponse toResponse(OnboardingBuddy b) {
        Optional<Employee> hire = employees.findById(b.getEmployeeId());
        Optional<Employee> buddy = employees.findById(b.getBuddyEmployeeId());
        return new BuddyResponse(
                b.getId(), b.getEmployeeId(), hire.map(OnboardingBuddyService::name).orElse(null),
                b.getBuddyEmployeeId(), buddy.map(OnboardingBuddyService::name).orElse(null),
                buddy.map(Employee::getEmployeeNo).orElse(null),
                b.getRole().name(), b.getStatus().name(), b.getNotes(),
                b.getAssignedBy(), b.getAssignedAt(), b.getEndedAt(),
                b.getTaskStatusId());
    }
}
