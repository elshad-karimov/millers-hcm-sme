package az.millers.hcm.corehr.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.HealthRequest;
import az.millers.hcm.corehr.api.dto.HealthResponse;
import az.millers.hcm.corehr.domain.EmployeeHealth;
import az.millers.hcm.corehr.repo.EmployeeHealthRepository;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Manages the one-per-employee {@link EmployeeHealth} record (M65 / P1-14).
 *
 * <p>Role-based access is enforced at the controller layer via {@code @PreAuthorize}
 * — this service is intentionally role-agnostic so it can be reused from
 * future occupational-health workflows. Audit log captures every read-side-effect-
 * free mutation; medical content stays encrypted at rest via the entity's
 * AttributeConverter.
 */
@Service
public class EmployeeHealthService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeHealth";

    private final EmployeeHealthRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public EmployeeHealthService(EmployeeHealthRepository repository,
                                  EmployeeRepository employees,
                                  AuditService audit,
                                  CurrentRequest currentRequest) {
        this.repository = repository;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public Optional<HealthResponse> getFor(UUID employeeId) {
        return repository.findByEmployeeId(employeeId).map(HealthResponse::from);
    }

    /**
     * Idempotent upsert — there's at most one row per employee
     * (V53 partial unique index). Creates on first call, updates thereafter.
     */
    @Transactional
    public HealthResponse upsert(UUID employeeId, HealthRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }

        EmployeeHealth h = repository.findByEmployeeId(employeeId)
                .orElseGet(() -> {
                    EmployeeHealth created = new EmployeeHealth();
                    created.setEmployeeId(employeeId);
                    created.setCreatedBy(currentRequest.username());
                    return created;
                });
        HealthResponse before = h.getId() == null ? null : HealthResponse.from(h);

        h.setFitnessCertificateDate(req.fitnessCertificateDate());
        h.setNextExamDate(req.nextExamDate());
        h.setOccupationalHealthNotes(req.occupationalHealthNotes());
        h.setRestrictions(req.restrictions());
        h.setConfidential(req.confidential() == null || req.confidential());
        // M137 — Section 18 disability. All four fields are nullable;
        // null clears (matches the rest of the apply() shape).
        h.setDisabilityStatus(req.disabilityStatus());
        h.setDisabilityPercent(req.disabilityPercent());
        h.setDisabilityNote(req.disabilityNote());
        h.setAccommodationsNote(req.accommodationsNote());
        h.setUpdatedBy(currentRequest.username());
        EmployeeHealth saved = repository.save(h);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                before == null ? "CREATE" : "UPDATE",
                before, HealthResponse.from(saved));
        return HealthResponse.from(saved);
    }

    @Transactional
    public void delete(UUID employeeId) {
        repository.findByEmployeeId(employeeId).ifPresent(h -> {
            HealthResponse before = HealthResponse.from(h);
            repository.delete(h);
            audit.record(MODULE, ENTITY, h.getId().toString(),
                    "DELETE", before, null);
        });
    }
}
