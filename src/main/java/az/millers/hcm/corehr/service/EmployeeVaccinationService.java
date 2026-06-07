package az.millers.hcm.corehr.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.VaccinationRequest;
import az.millers.hcm.corehr.api.dto.VaccinationResponse;
import az.millers.hcm.corehr.domain.EmployeeVaccination;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.corehr.repo.EmployeeVaccinationRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M137 — manages per-dose vaccination records. Same role gate as
 * {@link EmployeeHealthService} (occupational health roles only) —
 * enforced at the controller boundary.
 */
@Service
public class EmployeeVaccinationService {

    private static final String MODULE = "COREHR";
    private static final String ENTITY = "EmployeeVaccination";

    private final EmployeeVaccinationRepository vaccinations;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public EmployeeVaccinationService(EmployeeVaccinationRepository vaccinations,
                                       EmployeeRepository employees,
                                       AuditService audit,
                                       CurrentRequest currentRequest) {
        this.vaccinations = vaccinations;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<EmployeeVaccination> listFor(UUID employeeId) {
        return vaccinations.findByEmployeeIdOrderByAdministeredDateDesc(employeeId);
    }

    @Transactional
    public EmployeeVaccination create(UUID employeeId, VaccinationRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        validatePair(req);
        EmployeeVaccination v = new EmployeeVaccination();
        v.setEmployeeId(employeeId);
        apply(v, req);
        v.setCreatedBy(currentRequest.username());
        v.setUpdatedBy(currentRequest.username());
        EmployeeVaccination saved = vaccinations.save(v);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, VaccinationResponse.from(saved));
        return saved;
    }

    @Transactional
    public EmployeeVaccination update(UUID id, VaccinationRequest req) {
        EmployeeVaccination v = vaccinations.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Vaccination not found: " + id));
        validatePair(req);
        VaccinationResponse before = VaccinationResponse.from(v);
        apply(v, req);
        v.setUpdatedBy(currentRequest.username());
        EmployeeVaccination saved = vaccinations.save(v);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "UPDATE", before, VaccinationResponse.from(saved));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        EmployeeVaccination v = vaccinations.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Vaccination not found: " + id));
        VaccinationResponse before = VaccinationResponse.from(v);
        vaccinations.delete(v);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", before, null);
    }

    private static void validatePair(VaccinationRequest req) {
        if (req.nextDoseDate() != null
                && req.nextDoseDate().isBefore(req.administeredDate())) {
            throw new BadRequestException("nextDoseDate cannot be before administeredDate");
        }
    }

    private static void apply(EmployeeVaccination v, VaccinationRequest req) {
        v.setVaccineCode(req.vaccineCode());
        v.setVaccineName(req.vaccineName());
        v.setAdministeredDate(req.administeredDate());
        v.setAdministeredBy(req.administeredBy());
        v.setLotNumber(req.lotNumber());
        v.setNextDoseDate(req.nextDoseDate());
        v.setAttachmentUrl(req.attachmentUrl());
        v.setNotes(req.notes());
    }
}
