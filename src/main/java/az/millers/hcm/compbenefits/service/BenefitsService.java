package az.millers.hcm.compbenefits.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.EnrollmentRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.EnrollmentResponse;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.PlanRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.PlanResponse;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.TerminateRequest;
import az.millers.hcm.compbenefits.domain.BenefitCategory;
import az.millers.hcm.compbenefits.domain.BenefitCoverageTier;
import az.millers.hcm.compbenefits.domain.BenefitEnrollment;
import az.millers.hcm.compbenefits.domain.BenefitEnrollmentDependent;
import az.millers.hcm.compbenefits.domain.BenefitPlan;
import az.millers.hcm.compbenefits.domain.BenefitPlanTier;
import az.millers.hcm.compbenefits.domain.BenefitProvider;
import az.millers.hcm.compbenefits.domain.EnrollmentStatus;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.CoveredDependent;
import az.millers.hcm.compbenefits.repo.BenefitCategoryRepository;
import az.millers.hcm.compbenefits.repo.BenefitEnrollmentDependentRepository;
import az.millers.hcm.compbenefits.repo.BenefitEnrollmentRepository;
import az.millers.hcm.compbenefits.repo.BenefitPlanRepository;
import az.millers.hcm.compbenefits.repo.BenefitPlanTierRepository;
import az.millers.hcm.compbenefits.repo.BenefitProviderRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmployeeDependent;
import az.millers.hcm.corehr.repo.EmployeeDependentRepository;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.workflow.api.dto.ActionRequest;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.ActionType;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * M108 — Benefits administration: plan catalog CRUD + employee enrolment
 * lifecycle (enrol → waive → terminate). All side-effects are audited via
 * {@link AuditService}; reads are scope-checked by the controller layer.
 */
@Service
public class BenefitsService {

    private static final Logger log = LoggerFactory.getLogger(BenefitsService.class);

    private static final String MODULE = "COMP_BENEFITS";
    private static final String PLAN_ENTITY = "BenefitPlan";
    private static final String ENROLLMENT_ENTITY = "BenefitEnrollment";
    public static final String WORKFLOW_DEFINITION = "BENEFIT_ENROLLMENT_APPROVAL";

    private final BenefitPlanRepository plans;
    private final BenefitEnrollmentRepository enrollments;
    private final BenefitProviderRepository providers;
    private final BenefitCategoryRepository categories;
    private final BenefitPlanTierRepository tiers;
    private final EmployeeDependentRepository dependents;
    private final BenefitEnrollmentDependentRepository enrollmentDependents;
    private final EmployeeRepository employees;
    private final WorkflowService workflows;
    private final BenefitDeductionService benefitDeductions;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final TransactionTemplate requiresNew;

    public BenefitsService(BenefitPlanRepository plans,
                           BenefitEnrollmentRepository enrollments,
                           BenefitProviderRepository providers,
                           BenefitCategoryRepository categories,
                           BenefitPlanTierRepository tiers,
                           EmployeeDependentRepository dependents,
                           BenefitEnrollmentDependentRepository enrollmentDependents,
                           EmployeeRepository employees,
                           WorkflowService workflows,
                           BenefitDeductionService benefitDeductions,
                           NotificationService notifications,
                           AuditService audit,
                           CurrentRequest currentRequest,
                           PlatformTransactionManager txManager) {
        this.plans = plans;
        this.enrollments = enrollments;
        this.providers = providers;
        this.categories = categories;
        this.tiers = tiers;
        this.dependents = dependents;
        this.enrollmentDependents = enrollmentDependents;
        this.employees = employees;
        this.workflows = workflows;
        this.benefitDeductions = benefitDeductions;
        this.notifications = notifications;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Resolve a provider's display name, or null if unset / not found. */
    private String providerName(UUID providerId) {
        if (providerId == null) return null;
        return providers.findById(providerId).map(BenefitProvider::getName).orElse(null);
    }

    /** Resolve a category's display name, or null if unset / not found. */
    private String categoryName(UUID categoryId) {
        if (categoryId == null) return null;
        return categories.findById(categoryId).map(BenefitCategory::getName).orElse(null);
    }

    // -------------------------------------------------------------------------
    // Plan catalog
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans(boolean activeOnly) {
        List<BenefitPlan> rows = activeOnly
                ? plans.findByActiveTrueOrderByBenefitTypeAscNameAsc()
                : plans.findAllByOrderByBenefitTypeAscNameAsc();
        Map<UUID, String> providerNames = new HashMap<>();
        Map<UUID, String> categoryNames = new HashMap<>();
        return rows.stream()
                .map(p -> PlanResponse.from(p,
                        enrollments.countByPlanIdAndStatus(p.getId(), EnrollmentStatus.ENROLLED),
                        p.getProviderId() == null ? null
                                : providerNames.computeIfAbsent(p.getProviderId(), this::providerName),
                        p.getCategoryId() == null ? null
                                : categoryNames.computeIfAbsent(p.getCategoryId(), this::categoryName)))
                .toList();
    }

    @Transactional(readOnly = true)
    public BenefitPlan getPlan(UUID id) {
        return plans.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Benefit plan not found: " + id));
    }

    @Transactional(readOnly = true)
    public PlanResponse getPlanResponse(UUID id) {
        BenefitPlan p = getPlan(id);
        return PlanResponse.from(p,
                enrollments.countByPlanIdAndStatus(p.getId(), EnrollmentStatus.ENROLLED),
                providerName(p.getProviderId()), categoryName(p.getCategoryId()));
    }

    @Transactional
    public PlanResponse createPlan(PlanRequest req) {
        validatePlanRequest(req);
        if (plans.existsByCode(req.code())) {
            throw new BadRequestException("Benefit plan code already exists: " + req.code());
        }
        BenefitPlan p = new BenefitPlan();
        applyPlan(p, req);
        p.setCreatedBy(currentRequest.username());
        BenefitPlan saved = plans.save(p);
        PlanResponse response = PlanResponse.from(saved, 0L,
                providerName(saved.getProviderId()), categoryName(saved.getCategoryId()));
        audit.record(MODULE, PLAN_ENTITY, saved.getId().toString(),
                "CREATE", null, response);
        return response;
    }

    @Transactional
    public PlanResponse updatePlan(UUID id, PlanRequest req) {
        validatePlanRequest(req);
        BenefitPlan p = getPlan(id);
        long active = enrollments.countByPlanIdAndStatus(id, EnrollmentStatus.ENROLLED);
        PlanResponse before = PlanResponse.from(p, active,
                providerName(p.getProviderId()), categoryName(p.getCategoryId()));
        if (!p.getCode().equals(req.code()) && plans.existsByCode(req.code())) {
            throw new BadRequestException("Benefit plan code already exists: " + req.code());
        }
        applyPlan(p, req);
        BenefitPlan saved = plans.save(p);
        PlanResponse response = PlanResponse.from(saved, active,
                providerName(saved.getProviderId()), categoryName(saved.getCategoryId()));
        audit.record(MODULE, PLAN_ENTITY, id.toString(),
                "UPDATE", before, response);
        return response;
    }

    private void applyPlan(BenefitPlan p, PlanRequest req) {
        p.setCode(req.code());
        p.setName(req.name());
        p.setDescription(req.description());
        p.setBenefitType(req.benefitType());
        if (req.categoryId() != null && !categories.existsById(req.categoryId())) {
            throw new BadRequestException("Benefit category not found: " + req.categoryId());
        }
        p.setCategoryId(req.categoryId());
        p.setPlanYear(req.planYear());
        p.setProvider(req.provider());
        if (req.providerId() != null && !providers.existsById(req.providerId())) {
            throw new BadRequestException("Benefit provider not found: " + req.providerId());
        }
        p.setProviderId(req.providerId());
        p.setCoverageDetails(req.coverageDetails());
        p.setEligibility(req.eligibility());
        p.setEmployerContribution(zeroIfNull(req.employerContribution()));
        p.setEmployeeContribution(zeroIfNull(req.employeeContribution()));
        p.setCurrency(req.currency() == null || req.currency().isBlank()
                ? "AZN" : req.currency().toUpperCase());
        p.setEffectiveFrom(req.effectiveFrom());
        p.setEffectiveTo(req.effectiveTo());
        p.setActive(req.active() == null ? true : req.active());
    }

    private static BigDecimal zeroIfNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Package-private for direct testing — no DB needed. */
    static void validatePlanRequest(PlanRequest req) {
        if (req.employerContribution() != null && req.employerContribution().signum() < 0) {
            throw new BadRequestException("employerContribution must be non-negative");
        }
        if (req.employeeContribution() != null && req.employeeContribution().signum() < 0) {
            throw new BadRequestException("employeeContribution must be non-negative");
        }
        if (req.effectiveFrom() == null) {
            throw new BadRequestException("effectiveFrom is required");
        }
        if (req.effectiveTo() != null && req.effectiveTo().isBefore(req.effectiveFrom())) {
            throw new BadRequestException("effectiveTo must be on or after effectiveFrom");
        }
    }

    // -------------------------------------------------------------------------
    // Enrolments
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> listEnrolmentsForEmployee(UUID employeeId) {
        List<BenefitEnrollment> rows = enrollments.findByEmployeeIdOrderByStartDateDesc(employeeId);
        return decorate(rows);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> listEnrolmentsByStatus(EnrollmentStatus status) {
        List<BenefitEnrollment> rows = status == null
                ? enrollments.findAll().stream()
                    .sorted(Comparator.comparing(BenefitEnrollment::getStartDate).reversed())
                    .toList()
                : enrollments.findByStatusOrderByStartDateDesc(status);
        return decorate(rows);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> listEnrolmentsForPlan(UUID planId) {
        List<BenefitEnrollment> rows = enrollments.findByPlanIdOrderByStartDateDesc(planId);
        return decorate(rows);
    }

    private List<EnrollmentResponse> decorate(List<BenefitEnrollment> rows) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, BenefitPlan> planCache = new HashMap<>();
        Map<UUID, Employee> empCache = new HashMap<>();
        Map<UUID, String> depNameCache = new HashMap<>();
        // Batch-load covered-dependent links, grouped by enrollment.
        Map<UUID, List<CoveredDependent>> depsByEnrollment = new HashMap<>();
        List<UUID> ids = rows.stream().map(BenefitEnrollment::getId).toList();
        for (BenefitEnrollmentDependent link : enrollmentDependents.findByEnrollmentIdIn(ids)) {
            depsByEnrollment.computeIfAbsent(link.getEnrollmentId(), k -> new ArrayList<>())
                    .add(new CoveredDependent(link.getDependentId(),
                            depNameCache.computeIfAbsent(link.getDependentId(), this::dependentName)));
        }
        List<EnrollmentResponse> out = new ArrayList<>(rows.size());
        for (BenefitEnrollment e : rows) {
            BenefitPlan plan = planCache.computeIfAbsent(e.getPlanId(),
                    id -> plans.findById(id).orElse(null));
            Employee emp = empCache.computeIfAbsent(e.getEmployeeId(),
                    id -> employees.findById(id).orElse(null));
            String name = emp == null ? null
                    : (emp.getFirstName() + " " + emp.getLastName());
            out.add(EnrollmentResponse.from(e, plan, name,
                    depsByEnrollment.getOrDefault(e.getId(), List.of())));
        }
        return out;
    }

    private String dependentName(UUID depId) {
        return dependents.findById(depId)
                .map(d -> (d.getFirstName() + " " + d.getLastName()).trim())
                .orElse(null);
    }

    @Transactional
    public EnrollmentResponse enrol(EnrollmentRequest req) {
        BenefitPlan plan = getPlan(req.planId());
        if (!plan.isActive()) {
            throw new BadRequestException("Plan is not active: " + plan.getCode());
        }
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        EnrollmentStatus targetStatus = req.status() == null ? EnrollmentStatus.ENROLLED : req.status();
        if (targetStatus == EnrollmentStatus.TERMINATED) {
            throw new BadRequestException("Cannot create an enrolment in TERMINATED status; use /terminate");
        }
        if (targetStatus == EnrollmentStatus.ENROLLED
                && enrollments.existsByEmployeeIdAndPlanIdAndStatus(
                        req.employeeId(), req.planId(), EnrollmentStatus.ENROLLED)) {
            throw new BadRequestException("Employee is already actively enrolled in this plan");
        }

        // Resolve the contribution snapshot from the chosen coverage tier, else the flat plan rate.
        BigDecimal employerContribution = plan.getEmployerContribution();
        BigDecimal employeeContribution = plan.getEmployeeContribution();
        if (req.coverageTierCode() != null) {
            BenefitPlanTier tier = tiers.findByPlanIdAndActiveTrueOrderByDisplayOrderAsc(req.planId())
                    .stream().filter(t -> t.getTierCode() == req.coverageTierCode()).findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "Plan has no active tier " + req.coverageTierCode()));
            employerContribution = tier.getEmployerContribution();
            employeeContribution = tier.getEmployeeContribution();
        }

        // Validate the requested dependents belong to this employee and are benefit-eligible.
        List<UUID> depIds = req.dependentIds() == null ? List.of() : req.dependentIds();
        List<EmployeeDependent> validDeps = validateDependents(req.employeeId(), depIds);

        BenefitEnrollment e = new BenefitEnrollment();
        e.setPlanId(req.planId());
        e.setEmployeeId(req.employeeId());
        e.setStatus(targetStatus);
        e.setCoverageTierCode(req.coverageTierCode());
        e.setPlanYear(plan.getPlanYear());
        e.setEmployerContribution(employerContribution);
        e.setEmployeeContribution(employeeContribution);
        e.setCurrency(plan.getCurrency());
        e.setStartDate(req.startDate());
        e.setDependentsCovered(validDeps.isEmpty()
                ? (req.dependentsCovered() == null ? 0 : req.dependentsCovered())
                : validDeps.size());
        e.setNotes(req.notes());
        e.setEnrolledBy(currentRequest.username());
        e.setEnrolledAt(OffsetDateTime.now());
        BenefitEnrollment saved = enrollments.save(e);

        // Link the covered dependents.
        List<CoveredDependent> covered = new ArrayList<>();
        for (EmployeeDependent d : validDeps) {
            BenefitEnrollmentDependent link = new BenefitEnrollmentDependent();
            link.setEnrollmentId(saved.getId());
            link.setDependentId(d.getId());
            enrollmentDependents.save(link);
            covered.add(new CoveredDependent(d.getId(), (d.getFirstName() + " " + d.getLastName()).trim()));
        }

        Employee emp = employees.findById(saved.getEmployeeId()).orElse(null);
        String name = emp == null ? null : (emp.getFirstName() + " " + emp.getLastName());
        EnrollmentResponse response = EnrollmentResponse.from(saved, plan, name, covered);
        audit.record(MODULE, ENROLLMENT_ENTITY, saved.getId().toString(),
                targetStatus == EnrollmentStatus.WAIVED ? "WAIVE" : "ENROL",
                null, response);
        if (saved.getStatus() == EnrollmentStatus.ENROLLED) {
            onEnrollmentActivated(saved);
        }
        return response;
    }

    /** Validate dependents belong to the employee, are active + benefit/insurance-eligible + not eligibility-ended. */
    private List<EmployeeDependent> validateDependents(UUID employeeId, List<UUID> depIds) {
        if (depIds.isEmpty()) return List.of();
        LocalDate today = LocalDate.now();
        List<EmployeeDependent> out = new ArrayList<>(depIds.size());
        for (UUID depId : depIds) {
            EmployeeDependent d = dependents.findById(depId)
                    .orElseThrow(() -> new BadRequestException("Dependent not found: " + depId));
            if (!employeeId.equals(d.getEmployeeId())) {
                throw new BadRequestException("Dependent does not belong to this employee: " + depId);
            }
            if (!d.isActive()) {
                throw new BadRequestException("Dependent is not active: " + depId);
            }
            if (!d.isBenefitEligible() && !d.isInsuranceEligible()) {
                throw new BadRequestException("Dependent is not benefit/insurance eligible: " + depId);
            }
            if (d.getEligibilityEndDate() != null && !d.getEligibilityEndDate().isAfter(today)) {
                throw new BadRequestException("Dependent eligibility has ended: " + depId);
            }
            out.add(d);
        }
        return out;
    }

    /** Suspend an active enrollment (e.g. unpaid leave). Deduction paused by M378. */
    @Transactional
    public EnrollmentResponse suspend(UUID id) {
        BenefitEnrollment e = enrollments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrolment not found: " + id));
        if (e.getStatus() != EnrollmentStatus.ENROLLED) {
            throw new BadRequestException("Only ENROLLED enrolments can be suspended");
        }
        e.setStatus(EnrollmentStatus.SUSPENDED);
        BenefitEnrollment saved = enrollments.save(e);
        audit.record(MODULE, ENROLLMENT_ENTITY, id.toString(), "SUSPEND", null, saved.getStatus());
        onEnrollmentDeactivated(saved);
        return decorateOne(saved);
    }

    /** Resume a suspended enrollment back to ENROLLED. */
    @Transactional
    public EnrollmentResponse resume(UUID id) {
        BenefitEnrollment e = enrollments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrolment not found: " + id));
        if (e.getStatus() != EnrollmentStatus.SUSPENDED) {
            throw new BadRequestException("Only SUSPENDED enrolments can be resumed");
        }
        e.setStatus(EnrollmentStatus.ENROLLED);
        BenefitEnrollment saved = enrollments.save(e);
        audit.record(MODULE, ENROLLMENT_ENTITY, id.toString(), "RESUME", null, saved.getStatus());
        onEnrollmentActivated(saved);
        return decorateOne(saved);
    }

    private EnrollmentResponse decorateOne(BenefitEnrollment e) {
        return decorate(List.of(e)).get(0);
    }

    // -------------------------------------------------------------------------
    // M377 — enrollment approval workflow
    // -------------------------------------------------------------------------

    /** Submit a DRAFT enrollment for HR approval (starts the workflow). */
    @Transactional
    public EnrollmentResponse submit(UUID id) {
        BenefitEnrollment e = enrollments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrolment not found: " + id));
        if (e.getStatus() != EnrollmentStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT enrolments can be submitted (current: " + e.getStatus() + ")");
        }
        Employee emp = employees.findById(e.getEmployeeId()).orElse(null);
        BenefitPlan plan = plans.findById(e.getPlanId()).orElse(null);
        String who = emp == null ? e.getEmployeeId().toString() : (emp.getFirstName() + " " + emp.getLastName());
        String what = plan == null ? "benefit plan" : plan.getName();

        WorkflowInstance instance = workflows.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION, MODULE, ENROLLMENT_ENTITY, e.getId().toString(),
                "Benefit enrollment: " + who + " → " + what,
                Map.of(
                        "employeeId", e.getEmployeeId().toString(),
                        "planId", e.getPlanId().toString(),
                        "employeeContribution", e.getEmployeeContribution().toPlainString()
                )));

        e.setWorkflowInstanceId(instance.getId());
        e.setStatus(EnrollmentStatus.PENDING_APPROVAL);
        BenefitEnrollment saved = enrollments.save(e);
        audit.record(MODULE, ENROLLMENT_ENTITY, id.toString(), "SUBMIT",
                null, Map.of("workflowInstanceId", instance.getId().toString()));
        return decorateOne(saved);
    }

    /** Cancel a DRAFT / PENDING_APPROVAL enrollment before it becomes active. */
    @Transactional
    public EnrollmentResponse cancelDraft(UUID id) {
        BenefitEnrollment e = enrollments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrolment not found: " + id));
        if (e.getStatus() != EnrollmentStatus.DRAFT && e.getStatus() != EnrollmentStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only DRAFT or PENDING_APPROVAL enrolments can be cancelled");
        }
        if (e.getWorkflowInstanceId() != null) {
            try {
                workflows.act(e.getWorkflowInstanceId(),
                        new ActionRequest(ActionType.CANCEL, "Cancelled by requestor", null, null));
            } catch (Exception ex) {
                log.warn("Failed to cancel benefit-enrollment workflow {}: {}",
                        e.getWorkflowInstanceId(), ex.getMessage());
            }
        }
        e.setStatus(EnrollmentStatus.CANCELLED);
        BenefitEnrollment saved = enrollments.save(e);
        audit.record(MODULE, ENROLLMENT_ENTITY, id.toString(), "CANCEL", null, saved.getStatus());
        return decorateOne(saved);
    }

    /**
     * Workflow completion listener (AFTER_COMMIT, REQUIRES_NEW per the compensation pattern).
     * On APPROVED → the enrollment becomes ENROLLED (active); REJECTED → REJECTED.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        if (!WORKFLOW_DEFINITION.equals(event.definitionCode())) return;
        if (!ENROLLMENT_ENTITY.equals(event.subjectEntity())) return;

        UUID enrollmentId = UUID.fromString(event.subjectId());
        requiresNew.executeWithoutResult(status -> {
            BenefitEnrollment e = enrollments.findById(enrollmentId).orElse(null);
            if (e == null) {
                log.warn("Benefit enrollment not found for workflow completion: {}", enrollmentId);
                return;
            }
            // Idempotent — only act on a still-pending enrollment.
            if (e.getStatus() != EnrollmentStatus.PENDING_APPROVAL) return;

            switch (event.status()) {
                case APPROVED, AUTO_APPROVED -> {
                    e.setStatus(EnrollmentStatus.ENROLLED);
                    enrollments.save(e);
                    audit.record(MODULE, ENROLLMENT_ENTITY, enrollmentId.toString(), "APPROVED",
                            null, EnrollmentStatus.ENROLLED);
                    onEnrollmentActivated(e);
                    log.info("Benefit enrollment {} approved and activated", enrollmentId);
                }
                case REJECTED, RETURNED -> {
                    e.setStatus(EnrollmentStatus.REJECTED);
                    enrollments.save(e);
                    audit.record(MODULE, ENROLLMENT_ENTITY, enrollmentId.toString(), "REJECTED",
                            null, EnrollmentStatus.REJECTED);
                }
                case CANCELLED -> {
                    e.setStatus(EnrollmentStatus.CANCELLED);
                    enrollments.save(e);
                    audit.record(MODULE, ENROLLMENT_ENTITY, enrollmentId.toString(), "CANCELLED",
                            null, EnrollmentStatus.CANCELLED);
                }
                default -> log.warn("Unexpected workflow status {} for benefit enrollment {}",
                        event.status(), enrollmentId);
            }
        });
    }

    /**
     * Hook invoked when an enrollment becomes ENROLLED (active) — both direct enrol and
     * post-approval. M378 wires the payroll-deduction bridge here.
     */
    protected void onEnrollmentActivated(BenefitEnrollment e) {
        // M378 — bridge the employee contribution to payroll as a recurring deduction.
        benefitDeductions.ensureDeduction(e);
        // M387 — notify the employee that their coverage is active (non-fatal).
        try {
            Employee emp = employees.findById(e.getEmployeeId()).orElse(null);
            BenefitPlan plan = plans.findById(e.getPlanId()).orElse(null);
            if (emp != null && emp.getUsername() != null && !emp.getUsername().isBlank()) {
                String planName = plan == null ? "a benefit plan" : plan.getName();
                notifications.notifyAll(NotificationCategory.BENEFIT_ENROLLMENT, emp.getUsername(),
                        "Benefit coverage active: " + planName,
                        "Your enrollment in \"" + planName + "\" is now active"
                                + (e.getEmployeeContribution() != null && e.getEmployeeContribution().signum() > 0
                                    ? ". Your contribution of " + e.getEmployeeContribution() + " " + e.getCurrency()
                                      + "/month will be deducted via payroll." : "."),
                        MODULE, ENROLLMENT_ENTITY, e.getId().toString());
            }
        } catch (Exception ex) {
            log.warn("Benefit activation notification failed for enrollment {}: {}", e.getId(), ex.getMessage());
        }
    }

    /**
     * Hook invoked when an active enrollment is suspended or terminated. M378 cancels the
     * payroll deduction here.
     */
    protected void onEnrollmentDeactivated(BenefitEnrollment e) {
        // M378 — stop the recurring payroll deduction.
        benefitDeductions.cancelDeduction(e);
    }

    @Transactional
    public EnrollmentResponse terminate(UUID id, TerminateRequest req) {
        BenefitEnrollment e = enrollments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrolment not found: " + id));
        if (e.getStatus() == EnrollmentStatus.TERMINATED) {
            throw new BadRequestException("Enrolment is already TERMINATED");
        }
        if (req.endDate() == null) {
            throw new BadRequestException("endDate is required");
        }
        if (req.endDate().isBefore(e.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }
        BenefitPlan plan = plans.findById(e.getPlanId()).orElse(null);
        Employee emp = employees.findById(e.getEmployeeId()).orElse(null);
        String name = emp == null ? null : (emp.getFirstName() + " " + emp.getLastName());
        EnrollmentResponse before = EnrollmentResponse.from(e, plan, name);

        e.setStatus(EnrollmentStatus.TERMINATED);
        e.setEndDate(req.endDate());
        e.setTerminationReason(req.terminationReason());
        e.setTerminatedBy(currentRequest.username());
        e.setTerminatedAt(OffsetDateTime.now());
        BenefitEnrollment saved = enrollments.save(e);
        EnrollmentResponse response = EnrollmentResponse.from(saved, plan, name);
        audit.record(MODULE, ENROLLMENT_ENTITY, id.toString(), "TERMINATE",
                before, response);
        onEnrollmentDeactivated(saved);
        return response;
    }

    // -------------------------------------------------------------------------
    // Pure calculations (package-private for test access)
    // -------------------------------------------------------------------------

    /**
     * Total monthly cost an employer pays across a portfolio of plans, given the
     * currently-ENROLLED employee count per plan. Pure-math helper kept package
     * private so a unit test can pin it without spinning up Spring.
     */
    static BigDecimal totalEmployerSpend(List<BenefitPlan> plans,
                                         Map<UUID, Long> activeEnrolmentCount) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BenefitPlan p : plans) {
            long count = activeEnrolmentCount.getOrDefault(p.getId(), 0L);
            if (count <= 0) continue;
            sum = sum.add(p.getEmployerContribution().multiply(BigDecimal.valueOf(count)));
        }
        return sum;
    }
}
