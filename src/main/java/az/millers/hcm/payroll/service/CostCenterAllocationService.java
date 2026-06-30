package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.organization.domain.OrgUnit;
import az.millers.hcm.organization.repo.OrgUnitRepository;
import az.millers.hcm.payroll.domain.CostCenterAllocation;
import az.millers.hcm.payroll.repo.CostCenterAllocationRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M355 — Cost center allocation management.
 */
@Service
public class CostCenterAllocationService {

    private static final String MODULE = "PAYROLL";
    private static final String ENTITY = "CostCenterAllocation";

    private final CostCenterAllocationRepository allocations;
    private final EmployeeRepository employees;
    private final OrgUnitRepository orgUnits;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public CostCenterAllocationService(CostCenterAllocationRepository allocations,
                                        EmployeeRepository employees,
                                        OrgUnitRepository orgUnits,
                                        AuditService audit,
                                        CurrentRequest currentRequest) {
        this.allocations = allocations;
        this.employees = employees;
        this.orgUnits = orgUnits;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<CostCenterAllocation> list(UUID employeeId) {
        String tenantId = "default";
        return allocations.findByTenantIdAndEmployeeIdOrderByEffectiveFromDesc(tenantId, employeeId);
    }

    /**
     * Set new allocations for an employee effective from a date.
     * Validates sum = 100.00, closes prior open allocations.
     */
    @Transactional
    public void setAllocations(UUID employeeId, List<AllocationRequest> requests,
                                LocalDate effectiveFrom) {
        String tenantId = "default";

        Employee emp = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        // Validate sum = 100.00
        BigDecimal sum = requests.stream()
                .map(AllocationRequest::allocationPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(new BigDecimal("100.00")) != 0) {
            throw new BadRequestException("ALLOCATION_SUM_INVALID");
        }

        // Close prior open allocations
        List<CostCenterAllocation> priors = allocations.findByTenantIdAndEmployeeIdOrderByEffectiveFromDesc(
                tenantId, employeeId);
        LocalDate closureDate = effectiveFrom.minusDays(1);
        for (CostCenterAllocation prior : priors) {
            if (prior.getEffectiveTo() == null) {
                prior.setEffectiveTo(closureDate);
                allocations.save(prior);
                audit.record(MODULE, ENTITY, prior.getId().toString(), "CLOSED",
                        null, closureDate.toString());
            }
        }

        // Insert new allocations
        for (AllocationRequest req : requests) {
            CostCenterAllocation alloc = new CostCenterAllocation();
            alloc.setTenantId(tenantId);
            alloc.setEmployeeId(employeeId);
            alloc.setCostCenterCode(req.costCenterCode());
            alloc.setAllocationPct(req.allocationPct());
            alloc.setEffectiveFrom(effectiveFrom);
            allocations.save(alloc);

            audit.record(MODULE, ENTITY, alloc.getId().toString(), "CREATED",
                    null, req.costCenterCode() + " @ " + req.allocationPct() + "%");
        }
    }

    /**
     * Resolves active allocations for an employee on a date.
     * If none exist, returns single 100% allocation to employee's OrgUnit cost center,
     * or 'DEFAULT' if OrgUnit has none.
     */
    @Transactional(readOnly = true)
    public List<CostCenterAllocation> resolveAllocations(UUID employeeId, LocalDate periodDate) {
        String tenantId = "default";
        List<CostCenterAllocation> active = allocations.findActiveOn(tenantId, employeeId, periodDate);

        if (!active.isEmpty()) {
            return active;
        }

        // No explicit allocations — fall back to employee's OrgUnit cost center
        Employee emp = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        String costCenter = "DEFAULT";
        if (emp.getOrgUnitId() != null) {
            OrgUnit unit = orgUnits.findById(emp.getOrgUnitId()).orElse(null);
            if (unit != null && unit.getCostCentreCode() != null) {
                costCenter = unit.getCostCentreCode();
            }
        }

        CostCenterAllocation fallback = new CostCenterAllocation();
        fallback.setTenantId(tenantId);
        fallback.setEmployeeId(employeeId);
        fallback.setCostCenterCode(costCenter);
        fallback.setAllocationPct(new BigDecimal("100.00"));
        fallback.setEffectiveFrom(periodDate);

        List<CostCenterAllocation> result = new ArrayList<>();
        result.add(fallback);
        return result;
    }

    public record AllocationRequest(String costCenterCode, BigDecimal allocationPct) {}
}
