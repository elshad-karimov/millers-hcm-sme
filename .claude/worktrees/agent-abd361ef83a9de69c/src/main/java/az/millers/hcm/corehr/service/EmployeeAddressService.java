package az.millers.hcm.corehr.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.AddressRequest;
import az.millers.hcm.corehr.api.dto.AddressResponse;
import az.millers.hcm.corehr.domain.EmployeeAddress;
import az.millers.hcm.corehr.repo.EmployeeAddressRepository;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * CRUD for {@link EmployeeAddress} (M63 / P1-07).
 *
 * <p>Adding a new address for an {@code (employee, addressType)} pair closes
 * the prior open slice via the shared
 * {@link az.millers.hcm.common.history.EffectiveDatedRecord#closeOn(LocalDate)}
 * default method. Same exact mechanism the M62 history tables and the M62-era
 * {@link az.millers.hcm.payroll.service.CompensationService} use — no
 * duplicated date arithmetic.
 */
@Service
public class EmployeeAddressService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeAddress";

    private final EmployeeAddressRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;

    public EmployeeAddressService(EmployeeAddressRepository repository,
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
    public List<AddressResponse> listFor(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        return repository.findByEmployeeIdOrderByAddressTypeAscEffectiveFromDesc(employeeId)
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    /** Currently-active (open) addresses, one per type. */
    @Transactional(readOnly = true)
    public List<AddressResponse> currentFor(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        return repository.findOpenSlicesForEmployee(employeeId)
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse create(UUID employeeId, AddressRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        if (req.effectiveFrom() == null) {
            throw new BadRequestException("effectiveFrom is required");
        }

        // Close prior open slice for the same (employee, type) pair.
        repository.findOpenForEmployee(employeeId, req.addressType()).ifPresent(prior -> {
            if (prior.getEffectiveFrom().equals(req.effectiveFrom())) {
                // Same-day re-entry — supersede the prior open row instead of
                // closing it (closeOn would throw "strictly after").
                repository.delete(prior);
            } else {
                prior.closeOn(req.effectiveFrom());
                repository.save(prior);
            }
        });

        EmployeeAddress a = new EmployeeAddress();
        a.setEmployeeId(employeeId);
        apply(a, req);
        a.setEffectiveTo(null); // open-ended
        a.setCreatedBy(currentRequest.username());
        a.setUpdatedBy(currentRequest.username());
        EmployeeAddress saved = repository.save(a);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, AddressResponse.from(saved));
        return AddressResponse.from(saved);
    }

    @Transactional
    public AddressResponse update(UUID id, AddressRequest req) {
        EmployeeAddress a = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + id));
        if (req.addressType() != a.getAddressType()) {
            throw new BadRequestException(
                    "addressType cannot be changed — delete this slice and create a new one");
        }
        AddressResponse before = AddressResponse.from(a);

        // We allow editing the row in place — the effective_from / effective_to
        // are unchanged. This is the "fix a typo" path. Changing effective_from
        // would risk overlapping slices and is rejected.
        if (!req.effectiveFrom().equals(a.getEffectiveFrom())) {
            throw new BadRequestException(
                    "effectiveFrom is immutable — superseding this slice should be a new record instead");
        }
        apply(a, req);
        a.setUpdatedBy(currentRequest.username());
        EmployeeAddress saved = repository.save(a);

        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, AddressResponse.from(saved));
        return AddressResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        EmployeeAddress a = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + id));
        AddressResponse before = AddressResponse.from(a);
        repository.delete(a);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", before, null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void apply(EmployeeAddress a, AddressRequest req) {
        a.setAddressType(req.addressType());
        a.setAddressLine1(req.addressLine1());
        a.setAddressLine2(req.addressLine2());
        a.setCity(req.city());
        a.setDistrict(req.district());
        a.setCountry(req.country());
        a.setPostalCode(req.postalCode());
        a.setEffectiveFrom(req.effectiveFrom());
    }

    private void ensureEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }
}
