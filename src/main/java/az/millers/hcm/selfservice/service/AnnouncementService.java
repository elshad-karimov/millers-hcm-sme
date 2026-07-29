package az.millers.hcm.selfservice.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.selfservice.domain.Announcement;
import az.millers.hcm.selfservice.domain.AnnouncementAudience;
import az.millers.hcm.selfservice.repo.AnnouncementRepository;

/**
 * M430 — Announcement service (HR admin CRUD + employee active-for view).
 */
@Service
public class AnnouncementService {

    private static final String MODULE = "self-service";
    private static final String ENTITY = "Announcement";

    private final AnnouncementRepository repo;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public AnnouncementService(AnnouncementRepository repo,
                                EmployeeRepository employees,
                                AuditService audit,
                                CurrentRequest currentRequest) {
        this.repo = repo;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public Announcement create(String title, String body, LocalDate publishFrom, LocalDate publishTo,
                                 AnnouncementAudience audience, UUID audienceRef) {
        Announcement ann = new Announcement();
        ann.setTenantId(TenantContext.current());
        ann.setTitle(title);
        ann.setBody(body);
        ann.setPublishFrom(publishFrom);
        ann.setPublishTo(publishTo);
        ann.setAudience(audience);
        ann.setAudienceRef(audienceRef);
        ann.setActive(true);
        ann.setCreatedBy(currentRequest.username());
        ann.setUpdatedBy(currentRequest.username());
        Announcement saved = repo.save(ann);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null, null);
        return saved;
    }

    @Transactional
    public Announcement update(UUID id, String title, String body, LocalDate publishFrom, LocalDate publishTo,
                                AnnouncementAudience audience, UUID audienceRef, Boolean active) {
        Announcement ann = get(id);
        ann.setTitle(title);
        ann.setBody(body);
        ann.setPublishFrom(publishFrom);
        ann.setPublishTo(publishTo);
        ann.setAudience(audience);
        ann.setAudienceRef(audienceRef);
        if (active != null) {
            ann.setActive(active);
        }
        ann.setUpdatedBy(currentRequest.username());
        ann.setUpdatedAt(OffsetDateTime.now());
        Announcement saved = repo.save(ann);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", null, null);
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        Announcement ann = get(id);
        repo.delete(ann);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", null, null);
    }

    @Transactional(readOnly = true)
    public List<Announcement> list() {
        return repo.findByTenantIdAndActiveTrueOrderByPublishFromDesc(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public List<Announcement> listAll() {
        return repo.findAll().stream()
                .filter(a -> TenantContext.current().equals(a.getTenantId()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Announcement get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found: " + id));
    }

    /**
     * Filter announcements for the given employee: ALL audience + window match
     * + DEPARTMENT (exact match on org_unit) + LOCATION (exact match on work_location).
     */
    @Transactional(readOnly = true)
    public List<Announcement> activeFor(Employee employee) {
        LocalDate today = LocalDate.now();
        return repo.findByTenantIdAndActiveTrueOrderByPublishFromDesc(TenantContext.current()).stream()
                .filter(ann -> {
                    // Window check
                    if (ann.getPublishFrom().isAfter(today)) {
                        return false;
                    }
                    if (ann.getPublishTo() != null && ann.getPublishTo().isBefore(today)) {
                        return false;
                    }
                    // Audience check
                    if (ann.getAudience() == AnnouncementAudience.ALL) {
                        return true;
                    }
                    if (ann.getAudience() == AnnouncementAudience.DEPARTMENT && ann.getAudienceRef() != null) {
                        // Exact org_unit match (simple; could extend to descendants if helper exists)
                        return ann.getAudienceRef().equals(employee.getOrgUnitId());
                    }
                    if (ann.getAudience() == AnnouncementAudience.LOCATION && ann.getAudienceRef() != null) {
                        return ann.getAudienceRef().equals(employee.getWorkLocationId());
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }
}
