package az.millers.hcm.payroll.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.api.dto.PayrollGroupRequest;
import az.millers.hcm.payroll.api.dto.PayrollGroupResponse;
import az.millers.hcm.payroll.service.PayrollGroupService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payroll-groups")
public class PayrollGroupController {

    private final PayrollGroupService service;

    public PayrollGroupController(PayrollGroupService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','FINANCE_USER','AUDITOR')")
    public List<PayrollGroupResponse> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return service.list(activeOnly).stream().map(PayrollGroupResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','FINANCE_USER','AUDITOR')")
    public PayrollGroupResponse get(@PathVariable UUID id) {
        return PayrollGroupResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','FINANCE_USER')")
    public PayrollGroupResponse create(@Valid @RequestBody PayrollGroupRequest req) {
        return PayrollGroupResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','FINANCE_USER')")
    public PayrollGroupResponse update(@PathVariable UUID id, @Valid @RequestBody PayrollGroupRequest req) {
        return PayrollGroupResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
