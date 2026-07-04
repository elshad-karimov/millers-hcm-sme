package az.millers.hcm.compbenefits.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.AllowanceTypeRequest;
import az.millers.hcm.compbenefits.api.dto.AllowanceTypeResponse;
import az.millers.hcm.compbenefits.domain.AllowanceType;
import az.millers.hcm.compbenefits.repo.AllowanceTypeRepository;

@Service
public class AllowanceTypeService {

    private static final String MODULE = "COMP_BENEFITS";
    private static final String ENTITY = "AllowanceType";

    private final AllowanceTypeRepository types;
    private final AuditService audit;

    public AllowanceTypeService(AllowanceTypeRepository types, AuditService audit) {
        this.types = types;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AllowanceType> list(boolean activeOnly) {
        return activeOnly ? types.findByActiveTrueOrderByNameAsc() : types.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public AllowanceType get(UUID id) {
        return types.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Allowance type not found: " + id));
    }

    @Transactional
    public AllowanceType create(AllowanceTypeRequest req) {
        if (types.existsByCode(req.code())) {
            throw new BadRequestException("Allowance type code already exists: " + req.code());
        }
        AllowanceType t = new AllowanceType();
        apply(t, req);
        AllowanceType saved = types.save(t);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, AllowanceTypeResponse.from(saved));
        return saved;
    }

    @Transactional
    public AllowanceType update(UUID id, AllowanceTypeRequest req) {
        AllowanceType t = get(id);
        AllowanceTypeResponse before = AllowanceTypeResponse.from(t);
        apply(t, req);
        AllowanceType saved = types.save(t);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, AllowanceTypeResponse.from(saved));
        return saved;
    }

    private void apply(AllowanceType t, AllowanceTypeRequest req) {
        t.setCode(req.code());
        t.setName(req.name());
        t.setDescription(req.description());
        t.setCategory(req.category());
        t.setTaxable(req.taxable() == null ? true : req.taxable());
        t.setRecurring(req.recurring() == null ? true : req.recurring());
        t.setDefaultAmount(req.defaultAmount());
        t.setCurrency(req.currency() == null ? "AZN" : req.currency().toUpperCase());
        t.setActive(req.active() == null ? true : req.active());
    }
}
