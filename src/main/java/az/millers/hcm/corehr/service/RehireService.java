package az.millers.hcm.corehr.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.EmployeeResponse;
import az.millers.hcm.corehr.api.dto.RehireRequest;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.domain.EmploymentType;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Bring a previously-terminated employee back as a new row (M78 / P2-15).
 *
 * <p>This is intentionally a small specialised service rather than a flag on
 * {@code EmployeeService.create}: it keeps the audit story clean ("RE-HIRED
 * via prior EMP-00042") and avoids overloading the standard hire flow with
 * conditional logic for the {previous_employee_id} → field-copy step.
 */
@Service
public class RehireService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "Employee";

    private final EmployeeRepository repository;
    private final EmployeeHistoryService historyService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public RehireService(EmployeeRepository repository,
                          EmployeeHistoryService historyService,
                          AuditService audit,
                          CurrentRequest currentRequest) {
        this.repository = repository;
        this.historyService = historyService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public Employee rehire(RehireRequest req) {
        Employee prior = repository.findById(req.previousEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Previous employee not found: " + req.previousEmployeeId()));
        if (prior.getEmploymentStatus() != EmploymentStatus.TERMINATED
                && prior.getEmploymentStatus() != EmploymentStatus.RETIRED) {
            throw new BadRequestException(
                    "Can only rehire from a TERMINATED or RETIRED employee; "
                            + prior.getEmployeeNo() + " is " + prior.getEmploymentStatus());
        }
        if (!prior.isRehireEligible()) {
            throw new BadRequestException(
                    "Previous employee " + prior.getEmployeeNo()
                            + " is flagged not rehire-eligible");
        }
        if (req.newHireDate() == null || req.newHireDate().isBefore(prior.getHireDate())) {
            throw new BadRequestException(
                    "newHireDate must be on or after the prior hire date");
        }

        Employee fresh = new Employee();
        fresh.setEmployeeNo(String.format("EMP-%05d", repository.nextEmployeeNoSequence()));
        fresh.setFirstName(prior.getFirstName());
        fresh.setLastName(prior.getLastName());
        fresh.setMiddleName(prior.getMiddleName());
        fresh.setBirthDate(prior.getBirthDate());
        fresh.setGender(prior.getGender());
        fresh.setMaritalStatus(prior.getMaritalStatus());
        fresh.setNationality(prior.getNationality());
        fresh.setNationalId(prior.getNationalId());
        fresh.setEmail(prior.getEmail());
        fresh.setPhone(prior.getPhone());
        // New tenure starts now.
        fresh.setHireDate(req.newHireDate());
        // Allow overrides — fall back to prior employee's snapshot.
        fresh.setDepartmentName(req.departmentName() != null ? req.departmentName() : prior.getDepartmentName());
        fresh.setPositionTitle(req.positionTitle() != null ? req.positionTitle() : prior.getPositionTitle());
        fresh.setOrgUnitId(req.orgUnitId() != null ? req.orgUnitId() : prior.getOrgUnitId());
        fresh.setPositionId(req.positionId() != null ? req.positionId() : prior.getPositionId());
        fresh.setManagerId(req.managerId() != null ? req.managerId() : prior.getManagerId());
        fresh.setEmploymentType(prior.getEmploymentType() == null
                ? EmploymentType.PERMANENT : prior.getEmploymentType());
        fresh.setFtePercent(prior.getFtePercent() == null
                ? new BigDecimal("100.00") : prior.getFtePercent());
        fresh.setLeaveGroupId(prior.getLeaveGroupId());
        fresh.setPayrollGroupId(prior.getPayrollGroupId());
        fresh.setEmploymentStatus(EmploymentStatus.ON_PROBATION);
        fresh.setPreviousEmployeeId(prior.getId());
        fresh.setRehireReason(req.reason());
        fresh.setRehireEligible(true);
        fresh.setCreatedBy(currentRequest.username());
        fresh.setUpdatedBy(currentRequest.username());

        Employee saved = repository.save(fresh);

        // Open initial M62 history slices at the new hire date so downstream
        // tenure queries see a clean break from the prior employment.
        historyService.recordEmploymentSlice(saved, saved.getHireDate(),
                "Rehired from " + prior.getEmployeeNo(),
                MODULE, ENTITY, saved.getId().toString());
        historyService.recordStatusSlice(saved.getId(), saved.getEmploymentStatus(),
                saved.getHireDate(),
                "Rehired from " + prior.getEmployeeNo(),
                MODULE, ENTITY, saved.getId().toString());

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "REHIRE", EmployeeResponse.from(prior), EmployeeResponse.from(saved));
        return saved;
    }
}
