package az.millers.hcm.corehr.service;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.StatusOverlayRequest;
import az.millers.hcm.corehr.api.dto.StatusOverlayResponse;
import az.millers.hcm.corehr.domain.EmployeeStatusOverlay;
import az.millers.hcm.corehr.domain.EmployeeStatusOverlay.OverlaySource;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.corehr.repo.EmployeeStatusOverlayRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * Concurrent-status overlay service (M78 / P2-13).
 *
 * <p>Primary-only statuses cannot be used as overlays — they describe the
 * canonical state on the {@code employee.employment_status} column. The
 * DB CHECK constraint enforces the same set, so this validation is
 * belt-and-braces.
 */
@Service
public class EmployeeStatusOverlayService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeStatusOverlay";

    /** Statuses that may NOT appear as overlays — they're primary-only. */
    private static final Set<EmploymentStatus> PRIMARY_ONLY = EnumSet.of(
            EmploymentStatus.ACTIVE,
            EmploymentStatus.ON_PROBATION,
            EmploymentStatus.TERMINATED,
            EmploymentStatus.RETIRED,
            EmploymentStatus.CONTRACTOR,
            EmploymentStatus.INTERN);

    private final EmployeeStatusOverlayRepository overlays;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AccessScopeService scope;
    private final CurrentRequest currentRequest;

    public EmployeeStatusOverlayService(EmployeeStatusOverlayRepository overlays,
                                         EmployeeRepository employees,
                                         AuditService audit,
                                         AccessScopeService scope,
                                         CurrentRequest currentRequest) {
        this.overlays = overlays;
        this.employees = employees;
        this.audit = audit;
        this.scope = scope;
        this.currentRequest = currentRequest;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StatusOverlayResponse> listFor(UUID employeeId) {
        ensureAccessible(employeeId);
        return overlays.findByEmployeeIdOrderByEffectiveFromDesc(employeeId)
                .stream().map(StatusOverlayResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<StatusOverlayResponse> openFor(UUID employeeId) {
        ensureAccessible(employeeId);
        return overlays.findOpenForEmployee(employeeId)
                .stream().map(StatusOverlayResponse::from).toList();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Transactional
    public StatusOverlayResponse apply(UUID employeeId, StatusOverlayRequest req) {
        ensureAccessible(employeeId);
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        if (PRIMARY_ONLY.contains(req.status())) {
            throw new BadRequestException(
                    req.status() + " is a primary status and cannot be used as an overlay");
        }
        // Close any prior open overlay for the same (employee, status) — the
        // service-layer guarantee that pairs with the partial unique index.
        overlays.findOpenForEmployeeAndStatus(employeeId, req.status()).ifPresent(prior -> {
            if (prior.getEffectiveFrom().equals(req.effectiveFrom())) {
                overlays.delete(prior);
            } else {
                prior.closeOn(req.effectiveFrom());
                overlays.save(prior);
            }
        });

        EmployeeStatusOverlay o = new EmployeeStatusOverlay();
        o.setEmployeeId(employeeId);
        o.setStatus(req.status());
        o.setSource(req.source() == null ? OverlaySource.MANUAL : req.source());
        o.setSourceId(req.sourceId());
        o.setEffectiveFrom(req.effectiveFrom());
        o.setEffectiveTo(req.effectiveTo());
        o.setNotes(req.notes());
        o.setCreatedBy(currentRequest.username());
        o.setUpdatedBy(currentRequest.username());
        EmployeeStatusOverlay saved = overlays.save(o);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, StatusOverlayResponse.from(saved));
        return StatusOverlayResponse.from(saved);
    }

    @Transactional
    public StatusOverlayResponse close(UUID overlayId, LocalDate closeOn) {
        EmployeeStatusOverlay o = overlays.findById(overlayId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Overlay not found: " + overlayId));
        ensureAccessible(o.getEmployeeId());
        if (o.getEffectiveTo() != null) {
            throw new BadRequestException("Overlay is already closed");
        }
        LocalDate end = closeOn == null ? LocalDate.now() : closeOn;
        if (end.isBefore(o.getEffectiveFrom())) {
            throw new BadRequestException(
                    "closeOn (" + end + ") must be on or after effectiveFrom ("
                            + o.getEffectiveFrom() + ")");
        }
        StatusOverlayResponse before = StatusOverlayResponse.from(o);
        o.setEffectiveTo(end);
        o.setUpdatedBy(currentRequest.username());
        EmployeeStatusOverlay saved = overlays.save(o);
        audit.record(MODULE, ENTITY, overlayId.toString(),
                "CLOSE", before, StatusOverlayResponse.from(saved));
        return StatusOverlayResponse.from(saved);
    }

    @Transactional
    public void delete(UUID overlayId) {
        EmployeeStatusOverlay o = overlays.findById(overlayId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Overlay not found: " + overlayId));
        ensureAccessible(o.getEmployeeId());
        StatusOverlayResponse before = StatusOverlayResponse.from(o);
        overlays.delete(o);
        audit.record(MODULE, ENTITY, overlayId.toString(),
                "DELETE", before, null);
    }

    private void ensureAccessible(UUID employeeId) {
        if (!scope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }
}
