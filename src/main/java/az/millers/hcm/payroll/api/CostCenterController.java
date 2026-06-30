package az.millers.hcm.payroll.api;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.api.dto.CostAllocationRequest;
import az.millers.hcm.payroll.domain.CostCenterAllocation;
import az.millers.hcm.payroll.service.CostCenterAllocationService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M355 — Cost center allocation endpoints.
 */
@RestController
@RequestMapping("/api/payroll/employees/{employeeId}/cost-allocations")
public class CostCenterController {

    private final CostCenterAllocationService service;

    public CostCenterController(CostCenterAllocationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public ResponseEntity<List<CostCenterAllocation>> list(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(service.list(employeeId));
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public ResponseEntity<Void> setAllocations(
            @PathVariable UUID employeeId,
            @RequestBody CostAllocationRequest request) {

        List<CostCenterAllocationService.AllocationRequest> items = request.allocations().stream()
                .map(a -> new CostCenterAllocationService.AllocationRequest(
                        a.costCenterCode(), a.allocationPct()))
                .collect(Collectors.toList());

        service.setAllocations(employeeId, items, request.effectiveFrom());
        return ResponseEntity.ok().build();
    }
}
