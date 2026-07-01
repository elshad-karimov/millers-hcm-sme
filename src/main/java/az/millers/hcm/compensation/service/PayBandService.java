package az.millers.hcm.compensation.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compensation.api.dto.PayBandRequest;
import az.millers.hcm.compensation.api.dto.PayBandResponse;
import az.millers.hcm.compensation.domain.PayBand;
import az.millers.hcm.compensation.repo.PayBandRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M359 — Pay band CRUD service.
 * Validates min <= mid <= max, code uniqueness per tenant, effective date ranges.
 */
@Service
public class PayBandService {

    private static final String MODULE = "compensation";
    private static final String ENTITY = "PayBand";
    private static final String TENANT = "default";

    private final PayBandRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PayBandService(PayBandRepository repo,
                          AuditService audit,
                          CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<PayBand> listActive() {
        return repo.findByTenantIdAndIsActiveTrue(TENANT);
    }

    @Transactional(readOnly = true)
    public PayBand get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pay band not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<PayBand> findActiveOnForGrade(UUID gradeId, LocalDate date) {
        return repo.findActiveOnForGrade(TENANT, gradeId, date);
    }

    @Transactional(readOnly = true)
    public List<PayBand> findByGrade(UUID gradeId) {
        return repo.findByGradeId(gradeId);
    }

    @Transactional
    public PayBand create(PayBandRequest req) {
        validateRange(req.minSalary(), req.midSalary(), req.maxSalary());
        validateDateRange(req.effectiveFrom(), req.effectiveTo());

        if (repo.findByTenantIdAndCode(TENANT, req.code()).isPresent()) {
            throw new BadRequestException("BAND_CODE_DUPLICATE");
        }

        PayBand band = new PayBand();
        band.setTenantId(TENANT);
        applyRequest(band, req);

        PayBand saved = repo.save(band);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, PayBandResponse.from(saved));
        return saved;
    }

    @Transactional
    public PayBand update(UUID id, PayBandRequest req) {
        PayBand band = get(id);
        PayBandResponse before = PayBandResponse.from(band);

        validateRange(req.minSalary(), req.midSalary(), req.maxSalary());
        validateDateRange(req.effectiveFrom(), req.effectiveTo());

        // Check code uniqueness if changed
        if (!band.getCode().equals(req.code())
                && repo.findByTenantIdAndCode(TENANT, req.code()).isPresent()) {
            throw new BadRequestException("BAND_CODE_DUPLICATE");
        }

        applyRequest(band, req);
        PayBand saved = repo.save(band);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, PayBandResponse.from(saved));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        PayBand band = get(id);
        PayBandResponse before = PayBandResponse.from(band);

        // Soft delete — set is_active = false
        band.setIsActive(false);
        repo.save(band);

        audit.record(MODULE, ENTITY, id.toString(),
                "DEACTIVATE", before, PayBandResponse.from(band));
    }

    private void applyRequest(PayBand band, PayBandRequest req) {
        band.setCode(req.code());
        band.setName(req.name());
        band.setGradeId(req.gradeId());
        band.setCurrency(req.currency() != null ? req.currency() : "AZN");
        band.setMinSalary(req.minSalary());
        band.setMidSalary(req.midSalary());
        band.setMaxSalary(req.maxSalary());
        band.setQ1Salary(req.q1Salary());
        band.setQ3Salary(req.q3Salary());
        band.setEffectiveFrom(req.effectiveFrom());
        band.setEffectiveTo(req.effectiveTo());
        if (req.isActive() != null) {
            band.setIsActive(req.isActive());
        }
    }

    private void validateRange(BigDecimal min, BigDecimal mid, BigDecimal max) {
        if (min == null || mid == null || max == null) {
            throw new BadRequestException("min_salary, mid_salary, and max_salary are required");
        }
        if (min.compareTo(mid) > 0 || mid.compareTo(max) > 0) {
            throw new BadRequestException("BAND_RANGE_INVALID");
        }
    }

    private void validateDateRange(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveFrom == null) {
            throw new BadRequestException("effective_from is required");
        }
        if (effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
            throw new BadRequestException("effective_from cannot be after effective_to");
        }
    }
}
