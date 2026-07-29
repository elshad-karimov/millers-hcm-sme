package az.millers.hcm.compbenefits.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.OpenEnrollmentDtos.OpenStatus;
import az.millers.hcm.compbenefits.api.dto.OpenEnrollmentDtos.WindowRequest;
import az.millers.hcm.compbenefits.api.dto.OpenEnrollmentDtos.WindowResponse;
import az.millers.hcm.compbenefits.domain.OpenEnrollmentWindow;
import az.millers.hcm.compbenefits.repo.OpenEnrollmentWindowRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * HCM_11 M379 — open-enrollment window CRUD + "is enrollment open now" query.
 * Governance layer for employee self-election; HR admin enrolment is unrestricted.
 */
@Service
public class OpenEnrollmentService {

    private static final String MODULE = "COMP_BENEFITS";
    private static final String ENTITY = "OpenEnrollmentWindow";

    private final OpenEnrollmentWindowRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public OpenEnrollmentService(OpenEnrollmentWindowRepository repo,
                                 AuditService audit,
                                 CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<WindowResponse> list(boolean activeOnly) {
        LocalDate today = LocalDate.now();
        List<OpenEnrollmentWindow> rows = activeOnly
                ? repo.findByTenantIdAndActiveTrueOrderByStartDateDesc(TenantContext.current())
                : repo.findByTenantIdOrderByStartDateDesc(TenantContext.current());
        return rows.stream().map(w -> WindowResponse.from(w, today)).toList();
    }

    /** Whether an open-enrollment window is currently open (any plan year). */
    @Transactional(readOnly = true)
    public OpenStatus status() {
        LocalDate today = LocalDate.now();
        return repo.findByTenantIdAndActiveTrueOrderByStartDateDesc(TenantContext.current()).stream()
                .filter(w -> w.isOpenOn(today))
                .findFirst()
                .map(OpenStatus::of)
                .orElseGet(OpenStatus::closed);
    }

    @Transactional
    public WindowResponse create(WindowRequest req) {
        validate(req);
        OpenEnrollmentWindow w = new OpenEnrollmentWindow();
        w.setTenantId(TenantContext.current());
        apply(w, req);
        w.setCreatedBy(currentRequest.username());
        OpenEnrollmentWindow saved = repo.save(w);
        WindowResponse response = WindowResponse.from(saved, LocalDate.now());
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null, response);
        return response;
    }

    @Transactional
    public WindowResponse update(UUID id, WindowRequest req) {
        validate(req);
        OpenEnrollmentWindow w = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Open-enrollment window not found: " + id));
        WindowResponse before = WindowResponse.from(w, LocalDate.now());
        apply(w, req);
        OpenEnrollmentWindow saved = repo.save(w);
        WindowResponse response = WindowResponse.from(saved, LocalDate.now());
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", before, response);
        return response;
    }

    private void apply(OpenEnrollmentWindow w, WindowRequest req) {
        w.setPlanYear(req.planYear());
        w.setName(req.name());
        w.setStartDate(req.startDate());
        w.setEndDate(req.endDate());
        w.setNotes(req.notes());
        w.setActive(req.active() == null ? true : req.active());
    }

    private static void validate(WindowRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }
    }
}
