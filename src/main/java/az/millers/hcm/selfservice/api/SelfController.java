package az.millers.hcm.selfservice.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.businesstrip.api.dto.BusinessTripResponse;
import az.millers.hcm.businesstrip.api.dto.BusinessTripSubmitRequest;
import az.millers.hcm.businesstrip.repo.BusinessTripRequestRepository;
import az.millers.hcm.businesstrip.service.BusinessTripService;
import az.millers.hcm.compbenefits.api.dto.EmployeeAllowanceResponse;
import az.millers.hcm.compbenefits.domain.AllowanceStatus;
import az.millers.hcm.compbenefits.repo.EmployeeAllowanceRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.leave.api.dto.LeaveBalanceResponse;
import az.millers.hcm.leave.api.dto.LeaveRequestResponse;
import az.millers.hcm.leave.api.dto.LeaveSubmitRequest;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.repo.LeaveBalanceRepository;
import az.millers.hcm.leave.repo.LeaveRequestRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.leave.service.LeaveRequestService;
import az.millers.hcm.learning.api.dto.CertificateResponse;
import az.millers.hcm.learning.api.dto.EmployeeCompetencyResponse;
import az.millers.hcm.learning.api.dto.EnrollmentResponse;
import az.millers.hcm.learning.domain.EnrollmentStatus;
import az.millers.hcm.learning.repo.CertificateRepository;
import az.millers.hcm.learning.repo.CourseRepository;
import az.millers.hcm.learning.repo.EmployeeCompetencyRepository;
import az.millers.hcm.learning.repo.EnrollmentRepository;
import az.millers.hcm.payroll.api.dto.PayrollResultResponse;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.performance.api.dto.GoalResponse;
import az.millers.hcm.performance.api.dto.PerformanceReviewResponse;
import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.repo.GoalRepository;
import az.millers.hcm.performance.repo.PerformanceReviewRepository;
import az.millers.hcm.permission.api.dto.PermissionBalanceResponse;
import az.millers.hcm.permission.api.dto.PermissionRequestResponse;
import az.millers.hcm.permission.api.dto.PermissionSubmitRequest;
import az.millers.hcm.permission.repo.PermissionBalanceRepository;
import az.millers.hcm.permission.repo.PermissionRequestRepository;
import az.millers.hcm.permission.service.PermissionRequestService;
import az.millers.hcm.selfservice.api.dto.SelfDtos.SelfProfile;
import az.millers.hcm.selfservice.api.dto.SelfDtos.SelfSummary;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import az.millers.hcm.timesheet.api.dto.TimesheetResponse;
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.repo.TimesheetRepository;
import jakarta.validation.Valid;

/**
 * Employee Self-Service endpoints (PRD Section 11).
 *
 * <p>All endpoints are scoped to the {@link Employee} resolved from the
 * currently authenticated user. There is no employee-id query parameter —
 * the only way for an employee to see "someone else's" data here is to
 * have a different user account, which is what enforces the boundary.
 */
@RestController
@RequestMapping("/api/self")
@PreAuthorize("isAuthenticated()")
public class SelfController {

    private final EmployeeContextService context;
    private final EmployeeRepository employeeRepo;
    private final LeaveBalanceRepository leaveBalances;
    private final LeaveTypeRepository leaveTypes;
    private final LeaveRequestRepository leaveRequests;
    private final LeaveRequestService leaveRequestService;
    private final PermissionBalanceRepository permissionBalances;
    private final PermissionRequestRepository permissionRequests;
    private final PermissionRequestService permissionRequestService;
    private final BusinessTripRequestRepository businessTrips;
    private final BusinessTripService businessTripService;
    private final TimesheetRepository timesheets;
    private final PayrollResultRepository payrollResults;
    private final EnrollmentRepository enrollments;
    private final CertificateRepository certificates;
    private final CourseRepository courses;
    private final EmployeeCompetencyRepository employeeCompetencies;
    private final PerformanceReviewRepository reviews;
    private final GoalRepository goals;
    private final EmployeeAllowanceRepository allowances;

    public SelfController(EmployeeContextService context,
                          EmployeeRepository employeeRepo,
                          LeaveBalanceRepository leaveBalances,
                          LeaveTypeRepository leaveTypes,
                          LeaveRequestRepository leaveRequests,
                          LeaveRequestService leaveRequestService,
                          PermissionBalanceRepository permissionBalances,
                          PermissionRequestRepository permissionRequests,
                          PermissionRequestService permissionRequestService,
                          BusinessTripRequestRepository businessTrips,
                          BusinessTripService businessTripService,
                          TimesheetRepository timesheets,
                          PayrollResultRepository payrollResults,
                          EnrollmentRepository enrollments,
                          CertificateRepository certificates,
                          CourseRepository courses,
                          EmployeeCompetencyRepository employeeCompetencies,
                          PerformanceReviewRepository reviews,
                          GoalRepository goals,
                          EmployeeAllowanceRepository allowances) {
        this.context = context;
        this.employeeRepo = employeeRepo;
        this.leaveBalances = leaveBalances;
        this.leaveTypes = leaveTypes;
        this.leaveRequests = leaveRequests;
        this.leaveRequestService = leaveRequestService;
        this.permissionBalances = permissionBalances;
        this.permissionRequests = permissionRequests;
        this.permissionRequestService = permissionRequestService;
        this.businessTrips = businessTrips;
        this.businessTripService = businessTripService;
        this.timesheets = timesheets;
        this.payrollResults = payrollResults;
        this.enrollments = enrollments;
        this.certificates = certificates;
        this.courses = courses;
        this.employeeCompetencies = employeeCompetencies;
        this.reviews = reviews;
        this.goals = goals;
        this.allowances = allowances;
    }

    /** Lightweight peer projection for replacement-employee pickers. */
    public record EmployeePeer(java.util.UUID id, String employeeNo,
                               String firstName, String lastName) {}

    /**
     * Active colleagues the current user can pick as a replacement.
     * Excludes TERMINATED/RETIRED employees and the caller themselves.
     * Accessible to all authenticated users (no HR role required).
     */
    @GetMapping("/peers")
    public java.util.List<EmployeePeer> peers() {
        Employee me = context.currentEmployee();
        var excluded = java.util.Set.of(EmploymentStatus.TERMINATED, EmploymentStatus.RETIRED);
        return employeeRepo.findActiveColleagues(excluded)
                .stream()
                .filter(e -> !e.getId().equals(me.getId()))
                .map(e -> new EmployeePeer(e.getId(), e.getEmployeeNo(),
                        e.getFirstName(), e.getLastName()))
                .toList();
    }

    // ----- Profile + summary --------------------------------------------------

    @GetMapping("/employee")
    public SelfProfile profile() {
        Employee e = context.currentEmployee();
        return new SelfProfile(
                e.getId(), e.getEmployeeNo(), e.getUsername(),
                e.getFirstName(), e.getLastName(), e.getMiddleName(),
                e.getEmail(), e.getPhone(), e.getHireDate(),
                e.getEmploymentStatus() == null ? null : e.getEmploymentStatus().name(),
                e.getDepartmentName(), e.getPositionTitle(),
                e.getOrgUnitId(), e.getPositionId(), e.getManagerId());
    }

    @GetMapping("/summary")
    public SelfSummary summary() {
        Employee e = context.currentEmployee();
        int year = LocalDate.now().getYear();

        BigDecimal annualRemaining = null;
        long leavePending = leaveRequests.findByEmployeeIdOrderByStartDateDesc(
                e.getId(), PageRequest.of(0, 50)).stream()
                .filter(r -> r.getStatus() != null
                        && r.getStatus().name().equals("PENDING"))
                .count();

        var annualType = leaveTypes.findByCode("ANNUAL").orElse(null);
        if (annualType != null) {
            annualRemaining = leaveBalances
                    .findByEmployeeIdAndLeaveTypeIdAndYear(e.getId(), annualType.getId(), year)
                    .map(b -> b.remaining())
                    .orElse(null);
        }

        BigDecimal permissionRemaining = permissionBalances
                .findByEmployeeIdAndYearOrderByPermissionTypeId(e.getId(), year).stream()
                .map(b -> b.remaining())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long permissionPending = permissionRequests
                .findByEmployeeIdOrderByPermissionDateDesc(e.getId(), PageRequest.of(0, 50))
                .stream()
                .filter(r -> r.getStatus() != null && r.getStatus().name().equals("PENDING"))
                .count();

        LocalDate now = LocalDate.now();
        Timesheet currentTs = timesheets
                .findByEmployeeIdAndPeriodYearAndPeriodMonth(e.getId(), now.getYear(), now.getMonthValue())
                .orElse(null);

        List<PayrollResult> recentResults = payrollResults
                .findByEmployeeIdOrderByCreatedAtDesc(e.getId());
        OffsetDateTime lastPayslipAt = recentResults.isEmpty() ? null : recentResults.get(0).getCreatedAt();
        BigDecimal lastNet = recentResults.isEmpty() ? null : recentResults.get(0).getNetAmount();

        long mandatoryPublished = courses.findByMandatoryTrueAndStatusOrderByTitleAsc(
                az.millers.hcm.learning.domain.CourseStatus.PUBLISHED).size();
        long mandatoryPassed = enrollments.findByEmployeeIdOrderByEnrolledAtDesc(e.getId()).stream()
                .filter(en -> en.getStatus() == EnrollmentStatus.PASSED)
                .map(en -> en.getCourseId())
                .distinct()
                .filter(courseId -> courses.findById(courseId)
                        .map(c -> c.isMandatory() && c.getStatus()
                                == az.millers.hcm.learning.domain.CourseStatus.PUBLISHED)
                        .orElse(false))
                .count();
        long mandatoryPending = Math.max(0, mandatoryPublished - mandatoryPassed);
        long certs = certificates.findByEmployeeIdOrderByIssuedAtDesc(e.getId()).size();

        PerformanceReview latestReview = reviews
                .findByEmployeeIdOrderByCreatedAtDesc(e.getId(), PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);

        return new SelfSummary(
                e.getId(), e.getEmployeeNo(),
                e.getFirstName() + " " + e.getLastName(),
                e.getEmploymentStatus() == null ? null : e.getEmploymentStatus().name(),
                year,
                annualRemaining,
                leavePending,
                permissionRemaining,
                permissionPending,
                currentTs == null ? null : currentTs.getStatus().name(),
                lastPayslipAt,
                lastNet,
                mandatoryPending,
                certs,
                latestReview == null ? null
                        : reviewCycleCode(latestReview),
                latestReview == null ? null : latestReview.getStatus().name());
    }

    private String reviewCycleCode(PerformanceReview r) {
        // soft lookup; the cycle code is informational and not worth a join
        try {
            return r.getCycleId().toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    // ----- Self-submit shortcuts --------------------------------------------
    //
    // Each rebuilds the underlying SubmitRequest with employeeId forced to the
    // current user, so EMPLOYEE role can request leave / permission / a trip
    // without HR_ADMIN role.

    @PostMapping("/leave/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequestResponse submitLeave(@Valid @RequestBody LeaveSubmitRequest req) {
        Employee me = context.currentEmployee();
        LeaveSubmitRequest scoped = new LeaveSubmitRequest(
                me.getId(),
                req.leaveTypeId(),
                req.startDate(),
                req.endDate(),
                req.halfDay(),
                req.reason(),
                req.replacementEmployeeId(),
                req.attachmentUrl());
        return LeaveRequestResponse.from(leaveRequestService.submit(scoped));
    }

    @PostMapping("/permission/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public PermissionRequestResponse submitPermission(@Valid @RequestBody PermissionSubmitRequest req) {
        Employee me = context.currentEmployee();
        PermissionSubmitRequest scoped = new PermissionSubmitRequest(
                me.getId(),
                req.permissionTypeId(),
                req.permissionDate(),
                req.startTime(),
                req.endTime(),
                req.durationHours(),
                req.reason(),
                req.attachmentUrl());
        return PermissionRequestResponse.from(permissionRequestService.submit(scoped));
    }

    @PostMapping("/business-trips/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessTripResponse submitBusinessTrip(@Valid @RequestBody BusinessTripSubmitRequest req) {
        Employee me = context.currentEmployee();
        BusinessTripSubmitRequest scoped = new BusinessTripSubmitRequest(
                me.getId(),
                req.tripType(),
                req.destinationCountry(),
                req.destinationCity(),
                req.purpose(),
                req.project(),
                req.costCentre(),
                req.startDate(),
                req.endDate(),
                req.currency(),
                req.dailyAllowance(),
                req.requestedAdvance(),
                req.mealsProvided(),
                req.accommodationProvided(),
                req.attachmentUrls());
        return BusinessTripResponse.from(businessTripService.submit(scoped));
    }

    // ----- Leave -------------------------------------------------------------

    @GetMapping("/leave/balances")
    public List<LeaveBalanceResponse> leaveBalances() {
        Employee e = context.currentEmployee();
        int year = LocalDate.now().getYear();
        return leaveBalances.findByEmployeeIdAndYearOrderByLeaveTypeId(e.getId(), year)
                .stream().map(LeaveBalanceResponse::from).toList();
    }

    @GetMapping("/leave/types")
    public List<LeaveType> leaveTypes() {
        return leaveTypes.findByActiveTrueOrderByNameAsc();
    }

    @GetMapping("/leave/requests")
    public List<LeaveRequestResponse> leaveRequestsList() {
        Employee e = context.currentEmployee();
        return leaveRequests
                .findByEmployeeIdOrderByStartDateDesc(e.getId(), PageRequest.of(0, 200))
                .stream().map(LeaveRequestResponse::from).toList();
    }

    // ----- Permission --------------------------------------------------------

    @GetMapping("/permission/balances")
    public List<PermissionBalanceResponse> permissionBalances() {
        Employee e = context.currentEmployee();
        int year = LocalDate.now().getYear();
        return permissionBalances.findByEmployeeIdAndYearOrderByPermissionTypeId(e.getId(), year)
                .stream().map(PermissionBalanceResponse::from).toList();
    }

    @GetMapping("/permission/requests")
    public List<PermissionRequestResponse> permissionRequestsList() {
        Employee e = context.currentEmployee();
        return permissionRequests
                .findByEmployeeIdOrderByPermissionDateDesc(e.getId(), PageRequest.of(0, 200))
                .stream().map(PermissionRequestResponse::from).toList();
    }

    // ----- Business trips ----------------------------------------------------

    @GetMapping("/business-trips")
    public List<BusinessTripResponse> myTrips() {
        Employee e = context.currentEmployee();
        return businessTrips
                .findByEmployeeIdOrderByStartDateDesc(e.getId(), PageRequest.of(0, 200))
                .stream().map(BusinessTripResponse::from).toList();
    }

    // ----- Timesheets --------------------------------------------------------

    @GetMapping("/timesheets")
    public List<TimesheetResponse> myTimesheets() {
        Employee e = context.currentEmployee();
        return timesheets.findByEmployeeIdOrderByPeriodYearDescPeriodMonthDesc(e.getId())
                .stream()
                // List view doesn't need per-day rows — the detail page already
                // fetches them via the existing timesheets/{id} endpoint.
                .map(t -> TimesheetResponse.from(t, java.util.List.of()))
                .toList();
    }

    // ----- Payslips ----------------------------------------------------------

    @GetMapping("/payslips")
    public List<PayrollResultResponse> myPayslips() {
        Employee e = context.currentEmployee();
        return payrollResults.findByEmployeeIdOrderByCreatedAtDesc(e.getId())
                .stream().map(PayrollResultResponse::from).toList();
    }

    // ----- Learning ----------------------------------------------------------

    @GetMapping("/learning/enrollments")
    public List<EnrollmentResponse> myEnrollments() {
        Employee e = context.currentEmployee();
        return enrollments.findByEmployeeIdOrderByEnrolledAtDesc(e.getId())
                .stream().map(EnrollmentResponse::from).toList();
    }

    @GetMapping("/learning/certificates")
    public List<CertificateResponse> myCertificates() {
        Employee e = context.currentEmployee();
        return certificates.findByEmployeeIdOrderByIssuedAtDesc(e.getId())
                .stream().map(CertificateResponse::from).toList();
    }

    @GetMapping("/learning/competencies")
    public List<EmployeeCompetencyResponse> myCompetencies() {
        Employee e = context.currentEmployee();
        return employeeCompetencies.findByEmployeeIdOrderByAwardedAtDesc(e.getId())
                .stream().map(EmployeeCompetencyResponse::from).toList();
    }

    // ----- Performance -------------------------------------------------------

    @GetMapping("/performance/reviews")
    public List<PerformanceReviewResponse> myReviews() {
        Employee e = context.currentEmployee();
        return reviews
                .findByEmployeeIdOrderByCreatedAtDesc(e.getId(), PageRequest.of(0, 200))
                .stream().map(PerformanceReviewResponse::from).toList();
    }

    @GetMapping("/performance/goals")
    public List<GoalResponse> myGoals() {
        Employee e = context.currentEmployee();
        return goals.findByEmployeeIdOrderByCreatedAtDesc(e.getId())
                .stream().map(GoalResponse::from).toList();
    }

    // ----- Comp & benefits ---------------------------------------------------

    @GetMapping("/allowances")
    public List<EmployeeAllowanceResponse> myAllowances() {
        Employee e = context.currentEmployee();
        return allowances.findActiveForEmployeeOn(
                e.getId(), AllowanceStatus.ACTIVE, LocalDate.now())
                .stream().map(EmployeeAllowanceResponse::from).toList();
    }
}
