package az.millers.hcm.compbenefits.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compbenefits.api.dto.BenefitDtos.EnrollmentRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.EnrollmentResponse;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.PlanRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.PlanResponse;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.TerminateRequest;
import az.millers.hcm.compbenefits.domain.EnrollmentStatus;
import az.millers.hcm.compbenefits.service.BenefitsService;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import jakarta.validation.Valid;

/**
 * Benefits administration API (M108).
 *
 * <ul>
 *   <li>Plan catalog writes — HR_ADMIN only (high blast radius, affects payroll).</li>
 *   <li>Enrolment writes — HR_WRITE (HR specialists handle day-to-day enrol/waive/terminate).</li>
 *   <li>Reads — HR_PLUS_MANAGERS so managers can see their team's plans.</li>
 *   <li>{@code /me} — any authenticated employee can see their own enrolments.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/compbenefits/benefits")
public class BenefitsController {

    private final BenefitsService service;
    private final EmployeeContextService context;

    public BenefitsController(BenefitsService service, EmployeeContextService context) {
        this.service = service;
        this.context = context;
    }

    // ---------- Plans ----------

    @GetMapping("/plans")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<PlanResponse> listPlans(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.listPlans(activeOnly);
    }

    @GetMapping("/plans/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PlanResponse getPlan(@PathVariable UUID id) {
        return service.getPlanResponse(id);
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PlanResponse createPlan(@Valid @RequestBody PlanRequest req) {
        return service.createPlan(req);
    }

    @PutMapping("/plans/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PlanResponse updatePlan(@PathVariable UUID id, @Valid @RequestBody PlanRequest req) {
        return service.updatePlan(id, req);
    }

    // ---------- Enrolments (admin views) ----------

    @GetMapping("/enrollments")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<EnrollmentResponse> listEnrolments(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) UUID planId,
            @RequestParam(required = false) EnrollmentStatus status) {
        if (employeeId != null) {
            return service.listEnrolmentsForEmployee(employeeId);
        }
        if (planId != null) {
            return service.listEnrolmentsForPlan(planId);
        }
        return service.listEnrolmentsByStatus(status);
    }

    @PostMapping("/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public EnrollmentResponse enrol(@Valid @RequestBody EnrollmentRequest req) {
        return service.enrol(req);
    }

    @PostMapping("/enrollments/{id}/terminate")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public EnrollmentResponse terminate(@PathVariable UUID id,
                                        @Valid @RequestBody TerminateRequest req) {
        return service.terminate(id, req);
    }

    // ---------- Self-service ----------

    /** Employee's own enrolments — anybody authenticated can call. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public List<EnrollmentResponse> myEnrolments() {
        UUID empId = context.currentEmployee().getId();
        return service.listEnrolmentsForEmployee(empId);
    }
}
