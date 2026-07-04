package az.millers.hcm.leave.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.leave.domain.LeaveCategory;
import az.millers.hcm.leave.repo.LeaveCategoryRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class LeaveCategoryService {

    private static final String MODULE = "LEAVE";
    private static final String ENTITY = "LeaveCategory";

    private final LeaveCategoryRepository repository;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public LeaveCategoryService(LeaveCategoryRepository repository,
                                AuditService audit,
                                CurrentRequest currentRequest) {
        this.repository = repository;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<LeaveCategory> list() {
        return repository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional
    public LeaveCategory create(LeaveCategory category) {
        category.setCreatedBy(currentRequest.username());
        category.setUpdatedBy(currentRequest.username());
        LeaveCategory saved = repository.save(category);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public LeaveCategory update(UUID id, LeaveCategory req) {
        LeaveCategory existing = repository.findById(id)
                .orElseThrow(() -> new BadRequestException("Leave category not found: " + id));
        LeaveCategory before = clone(existing);
        existing.setName(req.getName());
        existing.setDescription(req.getDescription());
        existing.setPaidDefault(req.isPaidDefault());
        existing.setReportingGroup(req.getReportingGroup());
        existing.setUpdatedBy(currentRequest.username());
        LeaveCategory saved = repository.save(existing);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "UPDATE", before, saved);
        return saved;
    }

    @Transactional
    public void toggleActive(UUID id) {
        LeaveCategory category = repository.findById(id)
                .orElseThrow(() -> new BadRequestException("Leave category not found: " + id));
        boolean before = category.isActive();
        category.setActive(!before);
        category.setUpdatedBy(currentRequest.username());
        repository.save(category);
        audit.record(MODULE, ENTITY, category.getId().toString(),
                before ? "DEACTIVATE" : "ACTIVATE", before, !before);
    }

    private LeaveCategory clone(LeaveCategory c) {
        LeaveCategory copy = new LeaveCategory();
        copy.setId(c.getId());
        copy.setCode(c.getCode());
        copy.setName(c.getName());
        copy.setDescription(c.getDescription());
        copy.setPaidDefault(c.isPaidDefault());
        copy.setReportingGroup(c.getReportingGroup());
        copy.setActive(c.isActive());
        return copy;
    }
}
