package az.millers.hcm.lifecycle.api;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.lifecycle.api.dto.ContractChangeResponse;
import az.millers.hcm.lifecycle.api.dto.ContractChangeSubmitRequest;
import az.millers.hcm.lifecycle.domain.ChangeType;
import az.millers.hcm.lifecycle.domain.ContractChange;
import az.millers.hcm.lifecycle.domain.ContractChangeStatus;
import az.millers.hcm.lifecycle.service.ContractChangeService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/lifecycle/contract-changes")
public class ContractChangeController {

    private final ContractChangeService service;

    public ContractChangeController(ContractChangeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','DEPARTMENT_MANAGER')")
    public PageResponse<ContractChangeResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) ChangeType type,
            @RequestParam(required = false) ContractChangeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ContractChange> result = service.list(employeeId, type, status, PageRequest.of(page, size));
        return PageResponse.of(result, ContractChangeResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ContractChangeResponse get(@PathVariable UUID id) {
        return ContractChangeResponse.from(service.get(id));
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public ContractChangeResponse submit(@Valid @RequestBody ContractChangeSubmitRequest req) {
        return ContractChangeResponse.from(service.submit(req));
    }
}
