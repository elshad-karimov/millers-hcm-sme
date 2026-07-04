package az.millers.hcm.corehr.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.EmergencyContactRequest;
import az.millers.hcm.corehr.api.dto.EmergencyContactResponse;
import az.millers.hcm.corehr.domain.EmployeeEmergencyContact;
import az.millers.hcm.corehr.repo.EmployeeEmergencyContactRepository;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * CRUD for {@link EmployeeEmergencyContact} (M63 / P1-06).
 *
 * <p>Primary-contact invariant: a partial unique index in V51 enforces
 * "at most one primary per employee" at the DB level. The service additionally
 * demotes the prior primary when a new one is set, so the swap is atomic
 * within a single transaction.
 */
@Service
public class EmployeeEmergencyContactService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeEmergencyContact";

    private final EmployeeEmergencyContactRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;

    public EmployeeEmergencyContactService(EmployeeEmergencyContactRepository repository,
                                            EmployeeRepository employees,
                                            AuditService audit,
                                            AccessScopeService accessScope,
                                            CurrentRequest currentRequest) {
        this.repository = repository;
        this.employees = employees;
        this.audit = audit;
        this.accessScope = accessScope;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<EmergencyContactResponse> listFor(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        return repository.findByEmployeeIdOrderByPriorityOrderAsc(employeeId)
                .stream()
                .map(EmergencyContactResponse::from)
                .toList();
    }

    @Transactional
    public EmergencyContactResponse create(UUID employeeId, EmergencyContactRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }

        EmployeeEmergencyContact c = new EmployeeEmergencyContact();
        c.setEmployeeId(employeeId);
        apply(c, req);
        c.setCreatedBy(currentRequest.username());
        c.setUpdatedBy(currentRequest.username());

        // Promote-to-primary path: demote the prior primary inside the same TX.
        // Without this, the partial unique index would reject the insert.
        if (c.isPrimary()) demoteExistingPrimary(employeeId);

        EmployeeEmergencyContact saved = repository.save(c);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, EmergencyContactResponse.from(saved));
        return EmergencyContactResponse.from(saved);
    }

    @Transactional
    public EmergencyContactResponse update(UUID id, EmergencyContactRequest req) {
        EmployeeEmergencyContact c = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Emergency contact not found: " + id));
        EmergencyContactResponse before = EmergencyContactResponse.from(c);

        boolean wasPrimary = c.isPrimary();
        apply(c, req);
        c.setUpdatedBy(currentRequest.username());

        if (c.isPrimary() && !wasPrimary) {
            demoteExistingPrimary(c.getEmployeeId());
        }
        EmployeeEmergencyContact saved = repository.save(c);

        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, EmergencyContactResponse.from(saved));
        return EmergencyContactResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        EmployeeEmergencyContact c = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Emergency contact not found: " + id));
        EmergencyContactResponse before = EmergencyContactResponse.from(c);
        repository.delete(c);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", before, null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void apply(EmployeeEmergencyContact c, EmergencyContactRequest req) {
        c.setName(req.name());
        c.setRelationship(req.relationship());
        c.setPhone(req.phone());
        c.setAltPhone(req.altPhone());
        c.setEmail(req.email());
        c.setAddress(req.address());
        c.setPrimary(Boolean.TRUE.equals(req.primary()));
        c.setPriorityOrder(req.priorityOrder() != null ? req.priorityOrder() : 100);
    }

    private void demoteExistingPrimary(UUID employeeId) {
        repository.findByEmployeeIdAndPrimaryTrue(employeeId).ifPresent(prior -> {
            prior.setPrimary(false);
            prior.setUpdatedBy(currentRequest.username());
            repository.save(prior);
            // Flush so the unique-index check doesn't trip on a transient duplicate.
            repository.flush();
        });
    }

    private void ensureEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }
}
