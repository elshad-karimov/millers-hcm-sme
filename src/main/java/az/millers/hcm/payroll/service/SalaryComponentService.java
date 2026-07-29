package az.millers.hcm.payroll.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.api.dto.ComponentAssignmentRequest;
import az.millers.hcm.payroll.api.dto.SalaryComponentRequest;
import az.millers.hcm.payroll.domain.ComponentKind;
import az.millers.hcm.payroll.domain.SalaryComponent;
import az.millers.hcm.payroll.domain.SalaryComponentAssignment;
import az.millers.hcm.payroll.repo.SalaryComponentAssignmentRepository;
import az.millers.hcm.payroll.repo.SalaryComponentRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M349 — Salary component catalog management + per-employee assignment service.
 */
@Service
public class SalaryComponentService {

    private static final String MODULE = "payroll";
    private static final String ENTITY = "SalaryComponent";
    private static final String ENTITY_ASSIGNMENT = "SalaryComponentAssignment";

    private final SalaryComponentRepository repository;
    private final SalaryComponentAssignmentRepository assignmentRepo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final ObjectMapper objectMapper;

    public SalaryComponentService(SalaryComponentRepository repository,
                                   SalaryComponentAssignmentRepository assignmentRepo,
                                   AuditService audit,
                                   CurrentRequest currentRequest,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.assignmentRepo = assignmentRepo;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<SalaryComponent> list(ComponentKind kind, Boolean isActive, Boolean isStatutory, Pageable pageable) {
        return repository.findAll(kind, isActive, isStatutory, pageable);
    }

    @Transactional(readOnly = true)
    public SalaryComponent get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salary component not found: " + id));
    }

    @Transactional
    public SalaryComponent create(SalaryComponentRequest req) {
        repository.findByTenantIdAndCode(TenantContext.current(), req.code())
                .ifPresent(existing -> {
                    throw new BadRequestException("Component code already exists: " + req.code());
                });

        SalaryComponent c = new SalaryComponent();
        c.setCode(req.code());
        c.setName(req.name());
        c.setKind(req.kind());
        c.setCalculationMethod(req.calculationMethod());
        c.setDefaultAmount(req.defaultAmount());
        c.setPercentage(req.percentage());
        c.setIsTaxable(req.isTaxable() != null ? req.isTaxable() : true);
        c.setContributionExempt(req.contributionExempt() != null ? req.contributionExempt() : false);
        c.setIsStatutory(false);
        c.setIsActive(req.isActive() != null ? req.isActive() : true);

        SalaryComponent saved = repository.save(c);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATED", null, toMap(saved));

        return saved;
    }

    @Transactional
    public SalaryComponent update(UUID id, SalaryComponentRequest req) {
        SalaryComponent c = get(id);

        if (c.getIsStatutory()) {
            throw new BadRequestException("COMPONENT_IS_STATUTORY");
        }

        Optional<SalaryComponent> existing = repository.findByTenantIdAndCode(TenantContext.current(), req.code());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new BadRequestException("Component code already exists: " + req.code());
        }

        Map<String, Object> oldValue = toMap(c);

        c.setCode(req.code());
        c.setName(req.name());
        c.setKind(req.kind());
        c.setCalculationMethod(req.calculationMethod());
        c.setDefaultAmount(req.defaultAmount());
        c.setPercentage(req.percentage());
        c.setIsTaxable(req.isTaxable() != null ? req.isTaxable() : true);
        c.setContributionExempt(req.contributionExempt() != null ? req.contributionExempt() : false);
        c.setIsActive(req.isActive() != null ? req.isActive() : true);

        SalaryComponent saved = repository.save(c);

        audit.record(MODULE, ENTITY, id.toString(), "UPDATED", oldValue, toMap(saved));

        return saved;
    }

    @Transactional
    public void deactivate(UUID id) {
        SalaryComponent c = get(id);

        if (c.getIsStatutory()) {
            throw new BadRequestException("COMPONENT_IS_STATUTORY");
        }

        if (repository.hasActiveAssignments(id)) {
            throw new BadRequestException("COMPONENT_IN_USE");
        }

        Map<String, Object> oldValue = toMap(c);
        c.setIsActive(false);
        repository.save(c);

        audit.record(MODULE, ENTITY, id.toString(), "DEACTIVATED", oldValue, toMap(c));
    }

    // ── Assignment methods ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SalaryComponentAssignment> listAssignments(UUID employeeId, boolean activeOnly) {
        return assignmentRepo.findByEmployeeId(employeeId, activeOnly);
    }

    @Transactional
    public SalaryComponentAssignment createAssignment(UUID employeeId, ComponentAssignmentRequest req) {
        SalaryComponent component = get(req.componentId());

        // Auto-close prior open assignment for same (employee, component)
        List<SalaryComponentAssignment> open = assignmentRepo.findOpenByEmployeeAndComponent(
                employeeId, req.componentId());
        for (SalaryComponentAssignment prior : open) {
            prior.setEffectiveTo(req.effectiveFrom().minusDays(1));
            assignmentRepo.save(prior);
        }

        SalaryComponentAssignment a = new SalaryComponentAssignment();
        a.setEmployeeId(employeeId);
        a.setComponent(component);
        a.setAmountOverride(req.amountOverride());
        a.setEffectiveFrom(req.effectiveFrom());
        a.setEffectiveTo(null);
        a.setReason(req.reason());

        SalaryComponentAssignment saved = assignmentRepo.save(a);

        audit.record(MODULE, ENTITY_ASSIGNMENT, saved.getId().toString(), "CREATED", null,
                Map.of("employeeId", employeeId.toString(),
                       "componentId", req.componentId().toString(),
                       "effectiveFrom", req.effectiveFrom().toString()));

        return saved;
    }

    @Transactional
    public void closeAssignment(UUID employeeId, UUID assignmentId) {
        SalaryComponentAssignment a = assignmentRepo.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));

        if (!a.getEmployeeId().equals(employeeId)) {
            throw new BadRequestException("Assignment does not belong to employee");
        }

        if (a.getEffectiveTo() != null) {
            throw new BadRequestException("Assignment already closed");
        }

        a.setEffectiveTo(LocalDate.now());
        assignmentRepo.save(a);

        audit.record(MODULE, ENTITY_ASSIGNMENT, assignmentId.toString(), "CLOSED", null,
                Map.of("effectiveTo", LocalDate.now().toString()));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Map<String, Object> toMap(SalaryComponent c) {
        return Map.of(
                "code", c.getCode(),
                "name", c.getName(),
                "kind", c.getKind().name(),
                "calculationMethod", c.getCalculationMethod().name(),
                "isActive", c.getIsActive()
        );
    }
}
