package az.millers.hcm.learning.api;

import az.millers.hcm.security.SecurityRoles;

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

import az.millers.hcm.learning.api.dto.CompetencyRequest;
import az.millers.hcm.learning.api.dto.CompetencyResponse;
import az.millers.hcm.learning.api.dto.EmployeeCompetencyResponse;
import az.millers.hcm.learning.api.dto.EndorsementRequest;
import az.millers.hcm.learning.service.CompetencyService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/learning/competencies")
public class CompetencyController {

    private final CompetencyService service;

    public CompetencyController(CompetencyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CompetencyResponse> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return service.list(activeOnly).stream().map(CompetencyResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CompetencyResponse get(@PathVariable UUID id) {
        return CompetencyResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public CompetencyResponse create(@Valid @RequestBody CompetencyRequest req) {
        return CompetencyResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public CompetencyResponse update(@PathVariable UUID id, @Valid @RequestBody CompetencyRequest req) {
        return CompetencyResponse.from(service.update(id, req));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("isAuthenticated()")
    public List<EmployeeCompetencyResponse> forEmployee(@PathVariable UUID employeeId) {
        return service.forEmployee(employeeId).stream().map(EmployeeCompetencyResponse::from).toList();
    }

    // ── M136 — years of experience + endorsement ─────────────────────────

    /**
     * Set / clear the years-of-experience on an employee_competency.
     * Body: {@code {"years": 8.5}} or {@code {"years": null}} to clear.
     */
    @PutMapping("/employees/{employeeCompetencyId}/years")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public EmployeeCompetencyResponse setYears(
            @PathVariable UUID employeeCompetencyId,
            @RequestBody YearsRequest body) {
        return EmployeeCompetencyResponse.from(
                service.setYearsOfExperience(employeeCompetencyId, body.years()));
    }

    public record YearsRequest(java.math.BigDecimal years) {}

    /**
     * Manager / mentor endorsement. The endorser identity comes from the
     * auth context — never from the body — so a user can't endorse on
     * behalf of someone else. Endorsement updates the canonical
     * {@code proficiency} to match {@code endorsedLevel}.
     */
    @PostMapping("/employees/{employeeCompetencyId}/endorse")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public EmployeeCompetencyResponse endorse(
            @PathVariable UUID employeeCompetencyId,
            @Valid @RequestBody EndorsementRequest req) {
        return EmployeeCompetencyResponse.from(
                service.endorse(employeeCompetencyId, req));
    }
}
