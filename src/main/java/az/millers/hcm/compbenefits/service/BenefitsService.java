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
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.EnrollmentRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.EnrollmentResponse;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.PlanRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.PlanResponse;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.TerminateRequest;
import az.millers.hcm.compbenefits.domain.BenefitEnrollment;
import az.millers.hcm.compbenefits.domain.BenefitPlan;
import az.millers.hcm.compbenefits.domain.BenefitProvider;
import az.millers.hcm.compbenefits.domain.EnrollmentStatus;
import az.millers.hcm.compbenefits.repo.BenefitEnrollmentRepository;
import az.millers.hcm.compbenefits.repo.BenefitPlanRepository;
import az.millers.hcm.compbenefits.repo.BenefitProviderRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M108 — Benefits administration: plan catalog CRUD + employee enrolment
 * lifecycle (enrol → waive → terminate). All side-effects are audited via
 * {@link AuditService}; reads are scope-checked by the controller layer.
 */
@Service
public class BenefitsService {

    private static final String MODULE = "COMP_BENEFITS";
    private static final String PLAN_ENTITY = "BenefitPlan";
    private static final String ENROLLMENT_ENTITY = "BenefitEnrollment";

    private final BenefitPlanRepository plans;
    private final BenefitEnrollmentRepository enrollments;
    private final BenefitProviderRepository providers;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public BenefitsService(BenefitPlanRepository plans,
                           BenefitEnrollmentRepository enrollments,
                           BenefitProviderRepository providers,
                           EmployeeRepository employees,
                           AuditService audit,
                           CurrentRequest currentRequest) {
        this.plans = plans;
        this.enrollments = enrollments;
        this.providers = providers;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    /** Resolve a provider's display name, or null if unset / not found. */
    private String providerName(UUID providerId) {
        if (providerId == null) return null;
        return providers.findById(providerId).map(BenefitProvider::getName).orElse(null);
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
        return rows.stream()
                .map(p -> PlanResponse.from(p,
                        enrollments.countByPlanIdAndStatus(p.getId(), EnrollmentStatus.ENROLLED),
                        p.getProviderId() == null ? null
                                : providerNames.computeIfAbsent(p.getProviderId(), this::providerName)))
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
                providerName(p.getProviderId()));
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
        PlanResponse response = PlanResponse.from(saved, 0L, providerName(saved.getProviderId()));
        audit.record(MODULE, PLAN_ENTITY, saved.getId().toString(),
                "CREATE", null, response);
        return response;
    }

    @Transactional
    public PlanResponse updatePlan(UUID id, PlanRequest req) {
        validatePlanRequest(req);
        BenefitPlan p = getPlan(id);
        long active = enrollments.countByPlanIdAndStatus(id, EnrollmentStatus.ENROLLED);
        PlanResponse before = PlanResponse.from(p, active, providerName(p.getProviderId()));
        if (!p.getCode().equals(req.code()) && plans.existsByCode(req.code())) {
            throw new BadRequestException("Benefit plan code already exists: " + req.code());
        }
        applyPlan(p, req);
        BenefitPlan saved = plans.save(p);
        PlanResponse response = PlanResponse.from(saved, active, providerName(saved.getProviderId()));
        audit.record(MODULE, PLAN_ENTITY, id.toString(),
                "UPDATE", before, response);
        return response;
    }

    private void applyPlan(BenefitPlan p, PlanRequest req) {
        p.setCode(req.code());
        p.setName(req.name());
        p.setDescription(req.description());
        p.setBenefitType(req.benefitType());
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
        List<EnrollmentResponse> out = new ArrayList<>(rows.size());
        for (BenefitEnrollment e : rows) {
            BenefitPlan plan = planCache.computeIfAbsent(e.getPlanId(),
                    id -> plans.findById(id).orElse(null));
            Employee emp = empCache.computeIfAbsent(e.getEmployeeId(),
                    id -> employees.findById(id).orElse(null));
            String name = emp == null ? null
                    : (emp.getFirstName() + " " + emp.getLastName());
            out.add(EnrollmentResponse.from(e, plan, name));
        }
        return out;
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

        BenefitEnrollment e = new BenefitEnrollment();
        e.setPlanId(req.planId());
        e.setEmployeeId(req.employeeId());
        e.setStatus(targetStatus);
        e.setStartDate(req.startDate());
        e.setDependentsCovered(req.dependentsCovered() == null ? 0 : req.dependentsCovered());
        e.setNotes(req.notes());
        e.setEnrolledBy(currentRequest.username());
        e.setEnrolledAt(OffsetDateTime.now());
        BenefitEnrollment saved = enrollments.save(e);
        Employee emp = employees.findById(saved.getEmployeeId()).orElse(null);
        String name = emp == null ? null : (emp.getFirstName() + " " + emp.getLastName());
        EnrollmentResponse response = EnrollmentResponse.from(saved, plan, name);
        audit.record(MODULE, ENROLLMENT_ENTITY, saved.getId().toString(),
                targetStatus == EnrollmentStatus.WAIVED ? "WAIVE" : "ENROL",
                null, response);
        return response;
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
