package az.millers.hcm.compbenefits.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.EmployeeAllowanceRequest;
import az.millers.hcm.compbenefits.api.dto.EmployeeAllowanceResponse;
import az.millers.hcm.compbenefits.domain.AllowanceStatus;
import az.millers.hcm.compbenefits.domain.EmployeeAllowance;
import az.millers.hcm.compbenefits.repo.AllowanceTypeRepository;
import az.millers.hcm.compbenefits.repo.EmployeeAllowanceRepository;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.common.BusinessNumbers;

@Service
public class EmployeeAllowanceService {

    private static final String MODULE = "COMP_BENEFITS";
    private static final String ENTITY = "EmployeeAllowance";

    private final EmployeeAllowanceRepository allowances;
    private final AllowanceTypeRepository types;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public EmployeeAllowanceService(EmployeeAllowanceRepository allowances,
                                     AllowanceTypeRepository types,
                                     EmployeeRepository employees,
                                     AuditService audit,
                                     CurrentRequest currentRequest) {
        this.allowances = allowances;
        this.types = types;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public EmployeeAllowance get(UUID id) {
        return allowances.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Allowance not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeAllowance> historyFor(UUID employeeId) {
        return allowances.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public List<EmployeeAllowance> activeForEmployeeOn(UUID employeeId, LocalDate on) {
        return allowances.findActiveForEmployeeOn(employeeId, AllowanceStatus.ACTIVE, on);
    }

    @Transactional
    public EmployeeAllowance create(EmployeeAllowanceRequest req) {
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        if (!types.existsById(req.allowanceTypeId())) {
            throw new BadRequestException("Allowance type not found: " + req.allowanceTypeId());
        }
        if (req.amount() == null || req.amount().signum() < 0) {
            throw new BadRequestException("amount must be non-negative");
        }
        if (req.effectiveTo() != null && req.effectiveTo().isBefore(req.effectiveFrom())) {
            throw new BadRequestException("effectiveTo must be on or after effectiveFrom");
        }

        // Close any prior open record for the same (employee, type) by setting
        // its effective_to = new effective_from - 1 (consistent with payroll
        // compensation's approach).
        List<EmployeeAllowance> prior = allowances
                .findByEmployeeIdOrderByEffectiveFromDesc(req.employeeId());
        for (EmployeeAllowance p : prior) {
            if (!p.getAllowanceTypeId().equals(req.allowanceTypeId())) continue;
            if (p.getStatus() != AllowanceStatus.ACTIVE) continue;
            if (p.getEffectiveTo() != null && p.getEffectiveTo().isBefore(req.effectiveFrom())) continue;
            // overlapping → close it the day before
            p.setEffectiveTo(req.effectiveFrom().minusDays(1));
            if (!p.getEffectiveTo().isBefore(p.getEffectiveFrom())) {
                allowances.save(p);
            } else {
                // new effective_from is at or before existing's effective_from — cancel the prior
                p.setStatus(AllowanceStatus.CANCELLED);
                allowances.save(p);
            }
        }

        EmployeeAllowance a = new EmployeeAllowance();
        a.setAllowanceNo(BusinessNumbers.format("ALW", 5, allowances.nextNoSequence()));
        a.setEmployeeId(req.employeeId());
        a.setAllowanceTypeId(req.allowanceTypeId());
        a.setAmount(req.amount());
        a.setCurrency(req.currency() == null ? "AZN" : req.currency().toUpperCase());
        a.setEffectiveFrom(req.effectiveFrom());
        a.setEffectiveTo(req.effectiveTo());
        a.setStatus(AllowanceStatus.ACTIVE);
        a.setNote(req.note());
        a.setCreatedBy(currentRequest.username());
        a.setUpdatedBy(currentRequest.username());
        EmployeeAllowance saved = allowances.save(a);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, EmployeeAllowanceResponse.from(saved));
        return saved;
    }

    @Transactional
    public EmployeeAllowance end(UUID id, LocalDate endDate) {
        EmployeeAllowance a = get(id);
        if (a.getStatus() != AllowanceStatus.ACTIVE) {
            throw new BadRequestException("Allowance is not ACTIVE");
        }
        EmployeeAllowanceResponse before = EmployeeAllowanceResponse.from(a);
        a.setEffectiveTo(endDate);
        a.setStatus(AllowanceStatus.ENDED);
        a.setUpdatedBy(currentRequest.username());
        EmployeeAllowance saved = allowances.save(a);
        audit.record(MODULE, ENTITY, id.toString(),
                "END", before, EmployeeAllowanceResponse.from(saved));
        return saved;
    }

    @Transactional
    public EmployeeAllowance cancel(UUID id, String reason) {
        EmployeeAllowance a = get(id);
        if (a.getStatus() != AllowanceStatus.ACTIVE) {
            throw new BadRequestException("Allowance is not ACTIVE");
        }
        EmployeeAllowanceResponse before = EmployeeAllowanceResponse.from(a);
        a.setStatus(AllowanceStatus.CANCELLED);
        a.setNote((a.getNote() == null ? "" : a.getNote() + "\n") + "Cancelled: " + (reason == null ? "" : reason));
        a.setUpdatedBy(currentRequest.username());
        EmployeeAllowance saved = allowances.save(a);
        audit.record(MODULE, ENTITY, id.toString(),
                "CANCEL", before, EmployeeAllowanceResponse.from(saved));
        return saved;
    }
}
