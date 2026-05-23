package az.millers.hcm.permission.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.permission.api.dto.PermissionTypeRequest;
import az.millers.hcm.permission.api.dto.PermissionTypeResponse;
import az.millers.hcm.permission.domain.PermissionType;
import az.millers.hcm.permission.repo.PermissionTypeRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class PermissionTypeService {

    private static final String MODULE = "PERMISSION";

    private final PermissionTypeRepository repository;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PermissionTypeService(PermissionTypeRepository repository, AuditService audit,
                                  CurrentRequest currentRequest) {
        this.repository = repository;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<PermissionType> list() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<PermissionType> listActive() {
        return repository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public PermissionType get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission type not found: " + id));
    }

    @Transactional
    public PermissionType create(PermissionTypeRequest req) {
        if (repository.existsByCode(req.code())) {
            throw new BadRequestException("Permission type code already exists: " + req.code());
        }
        PermissionType t = new PermissionType();
        applyRequest(t, req);
        t.setCreatedBy(currentRequest.username());
        t.setUpdatedBy(currentRequest.username());
        PermissionType saved = repository.save(t);
        audit.record(MODULE, "PermissionType", saved.getId().toString(),
                "CREATE", null, PermissionTypeResponse.from(saved));
        return saved;
    }

    @Transactional
    public PermissionType update(UUID id, PermissionTypeRequest req) {
        PermissionType t = get(id);
        if (!t.getCode().equals(req.code()) && repository.existsByCode(req.code())) {
            throw new BadRequestException("Permission type code already exists: " + req.code());
        }
        PermissionTypeResponse before = PermissionTypeResponse.from(t);
        applyRequest(t, req);
        t.setUpdatedBy(currentRequest.username());
        PermissionType saved = repository.save(t);
        audit.record(MODULE, "PermissionType", id.toString(),
                "UPDATE", before, PermissionTypeResponse.from(saved));
        return saved;
    }

    private void applyRequest(PermissionType t, PermissionTypeRequest req) {
        t.setCode(req.code());
        t.setName(req.name());
        t.setDescription(req.description());
        t.setAnnualLimitHours(req.annualLimitHours());
        t.setPaid(req.paid() == null ? true : req.paid());
        t.setRequiresAttachment(Boolean.TRUE.equals(req.requiresAttachment()));
        t.setActive(req.active() == null ? true : req.active());
    }
}
