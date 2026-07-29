package az.millers.hcm.ehs.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.ehs.domain.IncidentSeverity;
import az.millers.hcm.ehs.domain.InjuryReport;
import az.millers.hcm.ehs.repo.InjuryReportRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M449 — EHS injury report service.
 * MEDICAL CONFIDENTIALITY: reads restricted to HR_ADMIN only (enforced in controller).
 */
@Service
public class InjuryReportService {

    private static final String MODULE = "ehs";
    private static final String ENTITY = "InjuryReport";

    private final InjuryReportRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public InjuryReportService(InjuryReportRepository repo,
                               AuditService audit,
                               CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public InjuryReport create(UUID incidentId,
                               UUID employeeId,
                               String injuryType,
                               String bodyPart,
                               IncidentSeverity severity,
                               Boolean medicalTreatment,
                               Boolean firstAid,
                               Boolean hospital,
                               Integer lostTimeDays,
                               Boolean restrictedDuty,
                               String insuranceClaimRef,
                               String notes) {

        InjuryReport report = new InjuryReport();
        report.setTenantId(TenantContext.current());
        report.setIncidentId(incidentId);
        report.setEmployeeId(employeeId);
        report.setInjuryType(injuryType);
        report.setBodyPart(bodyPart);
        report.setSeverity(severity);
        report.setMedicalTreatment(medicalTreatment != null ? medicalTreatment : false);
        report.setFirstAid(firstAid != null ? firstAid : false);
        report.setHospital(hospital != null ? hospital : false);
        report.setLostTimeDays(lostTimeDays != null ? lostTimeDays : 0);
        report.setRestrictedDuty(restrictedDuty != null ? restrictedDuty : false);
        report.setInsuranceClaimRef(insuranceClaimRef);
        report.setNotes(notes);
        report.setCreatedBy(currentRequest.username());
        report.setUpdatedBy(currentRequest.username());

        InjuryReport saved = repo.save(report);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATED", null, null);

        return saved;
    }

    @Transactional
    public InjuryReport update(UUID id,
                               String injuryType,
                               String bodyPart,
                               IncidentSeverity severity,
                               Boolean medicalTreatment,
                               Boolean firstAid,
                               Boolean hospital,
                               Integer lostTimeDays,
                               Boolean restrictedDuty,
                               String insuranceClaimRef,
                               String notes) {

        InjuryReport report = get(id);

        if (injuryType != null) report.setInjuryType(injuryType);
        if (bodyPart != null) report.setBodyPart(bodyPart);
        if (severity != null) report.setSeverity(severity);
        if (medicalTreatment != null) report.setMedicalTreatment(medicalTreatment);
        if (firstAid != null) report.setFirstAid(firstAid);
        if (hospital != null) report.setHospital(hospital);
        if (lostTimeDays != null) report.setLostTimeDays(lostTimeDays);
        if (restrictedDuty != null) report.setRestrictedDuty(restrictedDuty);
        if (insuranceClaimRef != null) report.setInsuranceClaimRef(insuranceClaimRef);
        if (notes != null) report.setNotes(notes);

        report.setUpdatedBy(currentRequest.username());
        report.setUpdatedAt(OffsetDateTime.now());

        InjuryReport updated = repo.save(report);

        audit.record(MODULE, ENTITY, id.toString(), "UPDATED", null, null);

        return updated;
    }

    @Transactional(readOnly = true)
    public InjuryReport get(UUID id) {
        InjuryReport report = repo.findByIdAndTenantId(id, TenantContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("Injury report not found: " + id));

        // Tenant post-check
        if (!TenantContext.current().equals(report.getTenantId())) {
            throw new ResourceNotFoundException("Injury report not found: " + id);
        }

        return report;
    }

    @Transactional(readOnly = true)
    public List<InjuryReport> list(UUID incidentId, UUID employeeId) {
        if (incidentId != null) {
            return repo.findByTenantIdAndIncidentIdOrderByCreatedAtDesc(TenantContext.current(), incidentId);
        } else if (employeeId != null) {
            return repo.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TenantContext.current(), employeeId);
        } else {
            return repo.findByTenantIdOrderByCreatedAtDesc(TenantContext.current());
        }
    }
}
