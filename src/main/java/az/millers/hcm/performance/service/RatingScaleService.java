package az.millers.hcm.performance.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.performance.api.dto.RatingScaleDtos.ScaleRequest;
import az.millers.hcm.performance.api.dto.RatingScaleDtos.ScaleResponse;
import az.millers.hcm.performance.api.dto.RatingScaleDtos.ScaleValueRequest;
import az.millers.hcm.performance.domain.RatingScale;
import az.millers.hcm.performance.domain.RatingScaleValue;
import az.millers.hcm.performance.repo.RatingScaleRepository;
import az.millers.hcm.performance.repo.RatingScaleValueRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * HCM_12 M388 — rating scale master CRUD (PRD §5.3) + the §18.3 numeric-score →
 * rating-label conversion used by the weighted-scoring engine (M394).
 */
@Service
public class RatingScaleService {

    private static final String MODULE = "PERFORMANCE";
    private static final String ENTITY = "RatingScale";

    private final RatingScaleRepository scales;
    private final RatingScaleValueRepository values;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public RatingScaleService(RatingScaleRepository scales,
                              RatingScaleValueRepository values,
                              AuditService audit,
                              CurrentRequest currentRequest) {
        this.scales = scales;
        this.values = values;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<ScaleResponse> list(boolean activeOnly) {
        List<RatingScale> rows = activeOnly
                ? scales.findByTenantIdAndActiveTrueOrderByScaleNameAsc(TenantContext.current())
                : scales.findByTenantIdOrderByScaleNameAsc(TenantContext.current());
        return rows.stream()
                .map(s -> ScaleResponse.from(s, values.findByScaleIdOrderByValueOrderAsc(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScaleResponse get(UUID id) {
        RatingScale s = load(id);
        return ScaleResponse.from(s, values.findByScaleIdOrderByValueOrderAsc(id));
    }

    private RatingScale load(UUID id) {
        return scales.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rating scale not found: " + id));
    }

    /** The tenant's default scale (used when a cycle doesn't pick one). */
    @Transactional(readOnly = true)
    public Optional<RatingScale> defaultScale() {
        return scales.findFirstByTenantIdAndIsDefaultTrue(TenantContext.current());
    }

    /**
     * §18.3 — convert a 0–100 numeric score to a rating label via the scale's bands.
     * Returns empty when the scale has no bands or the score falls outside them.
     */
    @Transactional(readOnly = true)
    public Optional<RatingScaleValue> convertScore(UUID scaleId, BigDecimal score) {
        if (score == null) return Optional.empty();
        return values.findByScaleIdOrderByValueOrderAsc(scaleId).stream()
                .filter(v -> v.getMinPercentage() != null && v.getMaxPercentage() != null)
                .filter(v -> score.compareTo(v.getMinPercentage()) >= 0
                        && score.compareTo(v.getMaxPercentage()) <= 0)
                .findFirst();
    }

    @Transactional
    public ScaleResponse create(ScaleRequest req) {
        String code = req.scaleCode().trim().toUpperCase();
        if (scales.existsByTenantIdAndScaleCode(TenantContext.current(), code)) {
            throw new BadRequestException("Rating scale code already exists: " + code);
        }
        validateValues(req.values());
        RatingScale s = new RatingScale();
        s.setTenantId(TenantContext.current());
        s.setScaleCode(code);
        apply(s, req);
        s.setCreatedBy(currentRequest.username());
        RatingScale saved = scales.save(s);
        replaceValues(saved.getId(), req.values());
        if (saved.isDefault()) clearOtherDefaults(saved.getId());
        ScaleResponse response = get(saved.getId());
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null, response);
        return response;
    }

    @Transactional
    public ScaleResponse update(UUID id, ScaleRequest req) {
        RatingScale s = load(id);
        ScaleResponse before = get(id);
        String code = req.scaleCode().trim().toUpperCase();
        if (!s.getScaleCode().equals(code) && scales.existsByTenantIdAndScaleCode(TenantContext.current(), code)) {
            throw new BadRequestException("Rating scale code already exists: " + code);
        }
        validateValues(req.values());
        s.setScaleCode(code);
        apply(s, req);
        scales.save(s);
        replaceValues(id, req.values());
        if (s.isDefault()) clearOtherDefaults(id);
        ScaleResponse response = get(id);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", before, response);
        return response;
    }

    private void apply(RatingScale s, ScaleRequest req) {
        s.setScaleName(req.scaleName());
        s.setScaleType(req.scaleType());
        s.setDescription(req.description());
        s.setActive(req.active() == null ? true : req.active());
        s.setDefault(Boolean.TRUE.equals(req.isDefault()));
    }

    private void replaceValues(UUID scaleId, List<ScaleValueRequest> reqs) {
        values.deleteByScaleId(scaleId);
        values.flush();
        int order = 1;
        for (ScaleValueRequest r : reqs) {
            RatingScaleValue v = new RatingScaleValue();
            v.setTenantId(TenantContext.current());
            v.setScaleId(scaleId);
            v.setValueOrder(order++);
            v.setRatingValue(r.ratingValue());
            v.setRatingLabel(r.ratingLabel());
            v.setDescription(r.description());
            v.setMinPercentage(r.minPercentage());
            v.setMaxPercentage(r.maxPercentage());
            v.setColorCode(r.colorCode());
            values.save(v);
        }
    }

    /** Only one default scale per tenant. */
    private void clearOtherDefaults(UUID keepId) {
        for (RatingScale other : scales.findByTenantIdOrderByScaleNameAsc(TenantContext.current())) {
            if (!other.getId().equals(keepId) && other.isDefault()) {
                other.setDefault(false);
                scales.save(other);
            }
        }
    }

    private static void validateValues(List<ScaleValueRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) {
            throw new BadRequestException("A rating scale needs at least one value");
        }
        // Score bands, where present, must be valid and non-overlapping (§18.3).
        BigDecimal prevMax = null;
        for (ScaleValueRequest r : reqs) {
            if (r.minPercentage() == null && r.maxPercentage() == null) continue;
            if (r.minPercentage() == null || r.maxPercentage() == null) {
                throw new BadRequestException("Score band needs both min and max percentage");
            }
            if (r.maxPercentage().compareTo(r.minPercentage()) < 0) {
                throw new BadRequestException("Score band max must be >= min");
            }
            if (prevMax != null && r.minPercentage().compareTo(prevMax) <= 0) {
                throw new BadRequestException("Score bands must not overlap and must ascend");
            }
            prevMax = r.maxPercentage();
        }
    }
}
