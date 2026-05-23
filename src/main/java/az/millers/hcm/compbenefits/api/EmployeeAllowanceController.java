package az.millers.hcm.compbenefits.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compbenefits.api.dto.EmployeeAllowanceRequest;
import az.millers.hcm.compbenefits.api.dto.EmployeeAllowanceResponse;
import az.millers.hcm.compbenefits.service.EmployeeAllowanceService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/compbenefits/allowances")
public class EmployeeAllowanceController {

    private final EmployeeAllowanceService service;

    public EmployeeAllowanceController(EmployeeAllowanceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<EmployeeAllowanceResponse> list(
            @RequestParam UUID employeeId,
            @RequestParam(required = false) LocalDate effectiveOn) {
        var rows = effectiveOn == null
                ? service.historyFor(employeeId)
                : service.activeForEmployeeOn(employeeId, effectiveOn);
        return rows.stream().map(EmployeeAllowanceResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
    public EmployeeAllowanceResponse create(@Valid @RequestBody EmployeeAllowanceRequest req) {
        return EmployeeAllowanceResponse.from(service.create(req));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
    public EmployeeAllowanceResponse end(@PathVariable UUID id, @RequestParam LocalDate endDate) {
        return EmployeeAllowanceResponse.from(service.end(id, endDate));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public EmployeeAllowanceResponse cancel(@PathVariable UUID id,
                                              @RequestParam(required = false) String reason) {
        return EmployeeAllowanceResponse.from(service.cancel(id, reason));
    }
}
