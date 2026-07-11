package az.millers.hcm.learning.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.learning.api.dto.EnrollRequest;
import az.millers.hcm.learning.api.dto.TrainingPlanDtos.EnrollAllResult;
import az.millers.hcm.learning.api.dto.TrainingPlanDtos.TrainingPlanItemRequest;
import az.millers.hcm.learning.api.dto.TrainingPlanDtos.TrainingPlanItemResponse;
import az.millers.hcm.learning.api.dto.TrainingPlanDtos.TrainingPlanRequest;
import az.millers.hcm.learning.api.dto.TrainingPlanDtos.TrainingPlanResponse;
import az.millers.hcm.learning.domain.Course;
import az.millers.hcm.learning.domain.EnrolledVia;
import az.millers.hcm.learning.domain.TrainingPlan;
import az.millers.hcm.learning.domain.TrainingPlanItem;
import az.millers.hcm.learning.repo.CourseRepository;
import az.millers.hcm.learning.repo.TrainingPlanItemRepository;
import az.millers.hcm.learning.repo.TrainingPlanRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.common.BusinessNumbers;

/**
 * Business logic for Training Plans (M157 / §8.14.2).
 *
 * <p>A Training Plan groups courses to be delivered to a department or org
 * unit in a given period.  When activated, {@link #enrollAll} batch-enrolls
 * every active employee in the scoped org unit (or all active employees if
 * no org unit is set) for every course in the plan.
 */
@Service
public class TrainingPlanService {

    private static final Logger log = LoggerFactory.getLogger(TrainingPlanService.class);

    private static final String MODULE = "LEARNING";
    private static final String ENTITY = "TrainingPlan";

    private final TrainingPlanRepository plans;
    private final TrainingPlanItemRepository items;
    private final CourseRepository courses;
    private final EmployeeRepository employees;
    private final EnrollmentService enrollmentService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public TrainingPlanService(TrainingPlanRepository plans,
                               TrainingPlanItemRepository items,
                               CourseRepository courses,
                               EmployeeRepository employees,
                               EnrollmentService enrollmentService,
                               AuditService audit,
                               CurrentRequest currentRequest) {
        this.plans             = plans;
        this.items             = items;
        this.courses           = courses;
        this.employees         = employees;
        this.enrollmentService = enrollmentService;
        this.audit             = audit;
        this.currentRequest    = currentRequest;
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    public List<TrainingPlanResponse> listAll() {
        return plans.findAllByOrderByCreatedAtDesc().stream()
                .map(p -> toResponse(p, resolveItems(p)))
                .toList();
    }

    public List<TrainingPlanResponse> listByStatus(String status) {
        return plans.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(p -> toResponse(p, resolveItems(p)))
                .toList();
    }

    public TrainingPlanResponse get(UUID id) {
        TrainingPlan p = require(id);
        return toResponse(p, resolveItems(p));
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional
    public TrainingPlanResponse create(TrainingPlanRequest req) {
        validateType(req.planType());
        TrainingPlan p = new TrainingPlan();
        p.setPlanNo(nextPlanNo());
        applyRequest(p, req);
        p.setCreatedBy(currentRequest.username());
        p = plans.save(p);
        audit.record(MODULE, ENTITY, p.getId().toString(), "CREATED",
                null, Map.of("planNo", p.getPlanNo(), "name", p.getName()));
        return toResponse(p, List.of());
    }

    @Transactional
    public TrainingPlanResponse update(UUID id, TrainingPlanRequest req) {
        TrainingPlan p = require(id);
        requireDraft(p, "update");
        validateType(req.planType());
        applyRequest(p, req);
        p = plans.save(p);
        return toResponse(p, resolveItems(p));
    }

    @Transactional
    public TrainingPlanResponse addItem(UUID planId, TrainingPlanItemRequest req) {
        TrainingPlan p = require(planId);
        requireDraft(p, "add items to");
        Course course = courses.findById(req.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + req.courseId()));

        TrainingPlanItem item = new TrainingPlanItem();
        item.setPlan(p);
        item.setCourseId(course.getId());
        item.setDueDate(req.dueDate());
        item.setPositionId(req.positionId());
        item.setNotes(req.notes());
        item.setSortOrder(req.sortOrder());
        items.save(item);
        p = plans.save(p);
        return toResponse(p, resolveItems(p));
    }

    @Transactional
    public TrainingPlanResponse removeItem(UUID planId, UUID itemId) {
        TrainingPlan p = require(planId);
        requireDraft(p, "remove items from");
        items.deleteById(itemId);
        return toResponse(p, resolveItems(p));
    }

    // ── Status transitions ───────────────────────────────────────────────────

    @Transactional
    public TrainingPlanResponse activate(UUID id) {
        TrainingPlan p = require(id);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new BadRequestException("Plan is not in DRAFT status");
        }
        if (p.getItems().isEmpty()) {
            throw new BadRequestException("Cannot activate a plan with no courses");
        }
        p.setStatus("ACTIVE");
        p.setActivatedAt(OffsetDateTime.now());
        p = plans.save(p);
        audit.record(MODULE, ENTITY, p.getId().toString(), "ACTIVATED", null, Map.of());
        return toResponse(p, resolveItems(p));
    }

    @Transactional
    public TrainingPlanResponse complete(UUID id) {
        TrainingPlan p = require(id);
        if (!"ACTIVE".equals(p.getStatus())) {
            throw new BadRequestException("Plan must be ACTIVE to complete");
        }
        p.setStatus("COMPLETED");
        p.setCompletedAt(OffsetDateTime.now());
        p = plans.save(p);
        return toResponse(p, resolveItems(p));
    }

    @Transactional
    public TrainingPlanResponse archive(UUID id) {
        TrainingPlan p = require(id);
        if ("DRAFT".equals(p.getStatus()) || "ACTIVE".equals(p.getStatus())
                || "COMPLETED".equals(p.getStatus())) {
            p.setStatus("ARCHIVED");
            p = plans.save(p);
            return toResponse(p, resolveItems(p));
        }
        throw new BadRequestException("Plan cannot be archived from status: " + p.getStatus());
    }

    // ── Enroll-all ───────────────────────────────────────────────────────────

    @Transactional
    public EnrollAllResult enrollAll(UUID planId) {
        TrainingPlan p = require(planId);
        if (!"ACTIVE".equals(p.getStatus())) {
            throw new BadRequestException("Plan must be ACTIVE to enroll employees");
        }

        List<TrainingPlanItem> planItems = items.findByPlan_IdOrderBySortOrderAsc(planId);
        if (planItems.isEmpty()) {
            return new EnrollAllResult(0, 0);
        }

        // Resolve target employees
        List<UUID> employeeIds;
        if (p.getOrgUnitId() != null) {
            employeeIds = employees.findIdsByOrgUnitIdIn(List.of(p.getOrgUnitId()));
        } else {
            employeeIds = employees.findIdsByEmploymentStatus(EmploymentStatus.ACTIVE);
        }

        int enrolled = 0;
        int skipped = 0;
        for (TrainingPlanItem item : planItems) {
            for (UUID empId : employeeIds) {
                try {
                    enrollmentService.enroll(new EnrollRequest(
                            item.getCourseId(), empId, EnrolledVia.ASSIGNED, item.getDueDate()));
                    enrolled++;
                } catch (BadRequestException e) {
                    // Already enrolled — skip silently
                    skipped++;
                }
            }
        }

        p.setEnrolledCount(p.getEnrolledCount() + enrolled);
        plans.save(p);

        log.info("TrainingPlan {} enroll-all: {} enrolled, {} skipped", p.getPlanNo(), enrolled, skipped);
        audit.record(MODULE, ENTITY, p.getId().toString(), "ENROLL_ALL",
                null, Map.of("enrolled", enrolled, "skipped", skipped));
        return new EnrollAllResult(enrolled, skipped);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private TrainingPlan require(UUID id) {
        return plans.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingPlan not found: " + id));
    }

    private void requireDraft(TrainingPlan p, String action) {
        if (!"DRAFT".equals(p.getStatus())) {
            throw new BadRequestException("Can only " + action + " a DRAFT plan");
        }
    }

    private void validateType(String type) {
        if (type != null && !List.of("DEPARTMENT", "ANNUAL", "COMPLIANCE", "CAREER_PATH").contains(type)) {
            throw new BadRequestException("Invalid planType: " + type);
        }
    }

    private void applyRequest(TrainingPlan p, TrainingPlanRequest req) {
        p.setName(req.name());
        p.setDescription(req.description());
        p.setPlanType(req.planType() != null ? req.planType() : "DEPARTMENT");
        p.setOrgUnitId(req.orgUnitId());
        p.setFiscalYear(req.fiscalYear());
        p.setDeadline(req.deadline());
        p.setOwnerId(req.ownerId());
    }

    private String nextPlanNo() {
        int next = plans.findMaxSeq() + 1;
        return BusinessNumbers.format("TP", 5, next);
    }

    private List<TrainingPlanItemResponse> resolveItems(TrainingPlan p) {
        List<TrainingPlanItem> itemList = p.getItems().isEmpty()
                ? items.findByPlan_IdOrderBySortOrderAsc(p.getId())
                : p.getItems();
        if (itemList.isEmpty()) return List.of();

        List<UUID> courseIds = itemList.stream().map(TrainingPlanItem::getCourseId).toList();
        Map<UUID, Course> courseMap = courses.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));

        List<TrainingPlanItemResponse> result = new ArrayList<>();
        for (TrainingPlanItem item : itemList) {
            Course c = courseMap.get(item.getCourseId());
            result.add(TrainingPlanItemResponse.from(
                    item,
                    c != null ? c.getTitle() : null,
                    c != null ? c.getCode() : null));
        }
        return result;
    }

    private TrainingPlanResponse toResponse(TrainingPlan p, List<TrainingPlanItemResponse> itemResponses) {
        return TrainingPlanResponse.from(p, itemResponses);
    }
}
