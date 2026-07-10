package az.millers.hcm.payroll.service;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.domain.LaborRate;
import az.millers.hcm.payroll.repo.LaborRateRepository;
import az.millers.hcm.security.CurrentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M485: Labor rate service.
 */
@Service
public class LaborRateService {

    private static final String MODULE = "payroll";
    private static final String TENANT_ID = "default";

    private final LaborRateRepository rateRepo;
    private final CurrentRequest currentRequest;
    private final AuditService audit;

    public LaborRateService(LaborRateRepository rateRepo,
                           CurrentRequest currentRequest,
                           AuditService audit) {
        this.rateRepo = rateRepo;
        this.currentRequest = currentRequest;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<LaborRate> listRates() {
        return rateRepo.findByTenantIdOrderByEffectiveFromDesc(TENANT_ID);
    }

    @Transactional(readOnly = true)
    public LaborRate getRate(UUID id) {
        return rateRepo.findByIdAndTenantId(id, TENANT_ID)
            .orElseThrow(() -> new ResourceNotFoundException("Labor rate not found"));
    }

    @Transactional
    public LaborRate createRate(LaborRate rate) {
        rate.setTenantId(TENANT_ID);
        rate.setCreatedBy(currentRequest.username());

        LaborRate saved = rateRepo.save(rate);
        audit.record(MODULE, "LaborRate", saved.getId().toString(), "CREATE", null,
            Map.of("gradeId", saved.getGradeId() != null ? saved.getGradeId().toString() : "null",
                "positionId", saved.getPositionId() != null ? saved.getPositionId().toString() : "null",
                "hourlyRate", saved.getHourlyRate(),
                "effectiveFrom", saved.getEffectiveFrom()));
        return saved;
    }

    @Transactional
    public LaborRate updateRate(UUID id, LaborRate updated) {
        LaborRate existing = getRate(id);
        Map<String, Object> old = Map.of("hourlyRate", existing.getHourlyRate(),
            "effectiveFrom", existing.getEffectiveFrom(),
            "effectiveTo", existing.getEffectiveTo() != null ? existing.getEffectiveTo() : "null");

        existing.setHourlyRate(updated.getHourlyRate());
        existing.setEffectiveFrom(updated.getEffectiveFrom());
        existing.setEffectiveTo(updated.getEffectiveTo());
        existing.setUpdatedBy(currentRequest.username());

        LaborRate saved = rateRepo.save(existing);
        audit.record(MODULE, "LaborRate", saved.getId().toString(), "UPDATE", old,
            Map.of("hourlyRate", saved.getHourlyRate(),
                "effectiveFrom", saved.getEffectiveFrom(),
                "effectiveTo", saved.getEffectiveTo() != null ? saved.getEffectiveTo() : "null"));
        return saved;
    }
}
