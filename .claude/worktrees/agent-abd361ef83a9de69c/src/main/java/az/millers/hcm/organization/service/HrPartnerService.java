package az.millers.hcm.organization.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.organization.api.dto.HrPartnerDtos.HrPartnerRequest;
import az.millers.hcm.organization.api.dto.HrPartnerDtos.HrPartnerResponse;
import az.millers.hcm.organization.domain.HrPartner;
import az.millers.hcm.organization.repo.HrPartnerRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M142 — CRUD for the HR Partner assignment registry (§24).
 *
 * <p>Invariants:
 * <ul>
 *   <li>A given employee can be assigned to the same org unit only once
 *       per {@code effective_from} date (DB unique; service mirrors it).</li>
 *   <li>Deactivate instead of hard-delete for auditability.</li>
 *   <li>Effective window is validated (to ≥ from).</li>
 * </ul>
 */
@Service
public class HrPartnerService {

    private static final String MODULE = "ORGANIZATION";
    private static final String ENTITY = "HrPartner";

    private final HrPartnerRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public HrPartnerService(HrPartnerRepository repo,
                             AuditService audit,
                             CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<HrPartner> listForUnit(UUID orgUnitId) {
        return repo.findByOrgUnitIdAndActiveTrueOrderByBackupAscCreatedAtAsc(orgUnitId);
    }

    @Transactional(readOnly = true)
    public List<HrPartner> listForEmployee(UUID employeeId) {
        return repo.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public HrPartner get(UUID id) {
        return repo.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("HR Partner assignment not found: " + id));
    }

    @Transactional
    public HrPartner create(HrPartnerRequest req) {
        validateWindow(req);
        if (repo.existsByOrgUnitIdAndEmployeeIdAndEffectiveFrom(
                req.orgUnitId(), req.employeeId(), req.effectiveFrom())) {
            throw new BadRequestException(
                    "An HRBP assignment already exists for this org unit / employee / effective-from combination");
        }
        HrPartner h = new HrPartner();
        h.setCreatedBy(currentRequest.username());
        h.setUpdatedBy(currentRequest.username());
        apply(h, req);
        HrPartner saved = repo.save(h);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, HrPartnerResponse.from(saved));
        return saved;
    }

    @Transactional
    public HrPartner update(UUID id, HrPartnerRequest req) {
        HrPartner h = get(id);
        validateWindow(req);
        HrPartnerResponse before = HrPartnerResponse.from(h);
        h.setUpdatedBy(currentRequest.username());
        apply(h, req);
        HrPartner saved = repo.save(h);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, HrPartnerResponse.from(saved));
        return saved;
    }

    @Transactional
    public HrPartner setActive(UUID id, boolean active) {
        HrPartner h = get(id);
        if (h.isActive() == active) return h;
        HrPartnerResponse before = HrPartnerResponse.from(h);
        h.setActive(active);
        h.setUpdatedBy(currentRequest.username());
        HrPartner saved = repo.save(h);
        audit.record(MODULE, ENTITY, id.toString(),
                active ? "REACTIVATE" : "DEACTIVATE",
                before, HrPartnerResponse.from(saved));
        return saved;
    }

    private void apply(HrPartner h, HrPartnerRequest req) {
        h.setOrgUnitId(req.orgUnitId());
        h.setEmployeeId(req.employeeId());
        h.setBackup(req.backup());
        h.setEffectiveFrom(req.effectiveFrom());
        h.setEffectiveTo(req.effectiveTo());
        h.setNotes(req.notes());
        if (req.active() != null) h.setActive(req.active());
    }

    private void validateWindow(HrPartnerRequest req) {
        if (req.effectiveFrom() != null && req.effectiveTo() != null
                && req.effectiveTo().isBefore(req.effectiveFrom())) {
            throw new BadRequestException("effectiveTo must not be before effectiveFrom");
        }
    }
}
