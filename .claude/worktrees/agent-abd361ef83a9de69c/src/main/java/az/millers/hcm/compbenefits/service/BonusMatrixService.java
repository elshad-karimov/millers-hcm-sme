package az.millers.hcm.compbenefits.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.BonusMatrixRuleRequest;
import az.millers.hcm.compbenefits.api.dto.BonusMatrixRuleResponse;
import az.millers.hcm.compbenefits.domain.BonusMatrixRule;
import az.millers.hcm.compbenefits.repo.BonusMatrixRuleRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class BonusMatrixService {

    private static final String MODULE = "COMP_BENEFITS";
    private static final String ENTITY = "BonusMatrixRule";

    private final BonusMatrixRuleRepository rules;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public BonusMatrixService(BonusMatrixRuleRepository rules,
                              AuditService audit,
                              CurrentRequest currentRequest) {
        this.rules = rules;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<BonusMatrixRule> listAll() {
        return rules.findAllByOrderByPriorityAscCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<BonusMatrixRule> activeOn(LocalDate on) {
        return rules.findActiveOn(on);
    }

    @Transactional(readOnly = true)
    public BonusMatrixRule get(UUID id) {
        return rules.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Matrix rule not found: " + id));
    }

    @Transactional
    public BonusMatrixRule create(BonusMatrixRuleRequest req) {
        if (rules.existsByCode(req.code())) {
            throw new BadRequestException("Matrix rule code already exists: " + req.code());
        }
        BonusMatrixRule r = new BonusMatrixRule();
        apply(r, req);
        r.setCreatedBy(currentRequest.username());
        r.setUpdatedBy(currentRequest.username());
        BonusMatrixRule saved = rules.save(r);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, BonusMatrixRuleResponse.from(saved));
        return saved;
    }

    @Transactional
    public BonusMatrixRule update(UUID id, BonusMatrixRuleRequest req) {
        BonusMatrixRule r = get(id);
        BonusMatrixRuleResponse before = BonusMatrixRuleResponse.from(r);
        apply(r, req);
        r.setUpdatedBy(currentRequest.username());
        BonusMatrixRule saved = rules.save(r);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, BonusMatrixRuleResponse.from(saved));
        return saved;
    }

    /**
     * Pick the rule that applies to a (recommendation, finalRating) tuple on a given
     * date. Priority: lower number wins; explicit recommendation match beats a
     * generic rating-band match.
     */
    @Transactional(readOnly = true)
    public Optional<BonusMatrixRule> lookup(String recommendation, BigDecimal finalRating, LocalDate on) {
        List<BonusMatrixRule> active = rules.findActiveOn(on);
        BonusMatrixRule best = null;
        for (BonusMatrixRule r : active) {
            if (!matches(r, recommendation, finalRating)) continue;
            if (best == null) {
                best = r;
                continue;
            }
            // Lower priority value beats higher
            if (r.getPriority() < best.getPriority()) {
                best = r;
                continue;
            }
            // Same priority — prefer the more specific (recommendation-match) rule
            if (r.getPriority() == best.getPriority()
                    && r.getMatchRecommendation() != null
                    && best.getMatchRecommendation() == null) {
                best = r;
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean matches(BonusMatrixRule r, String recommendation, BigDecimal finalRating) {
        boolean recOk = r.getMatchRecommendation() == null
                || (recommendation != null && r.getMatchRecommendation().equalsIgnoreCase(recommendation));
        boolean ratingOk;
        if (r.getMinRating() == null && r.getMaxRating() == null) {
            ratingOk = true;
        } else if (finalRating == null) {
            ratingOk = false;
        } else {
            ratingOk = finalRating.compareTo(r.getMinRating()) >= 0
                    && finalRating.compareTo(r.getMaxRating()) <= 0;
        }
        return recOk && ratingOk;
    }

    private void apply(BonusMatrixRule r, BonusMatrixRuleRequest req) {
        if (req.bonusPercent() == null && req.flatAmount() == null) {
            throw new BadRequestException("One of bonusPercent or flatAmount is required");
        }
        if ((req.minRating() == null) != (req.maxRating() == null)) {
            throw new BadRequestException("minRating and maxRating must be provided together");
        }
        if (req.minRating() != null && req.maxRating() != null
                && req.maxRating().compareTo(req.minRating()) < 0) {
            throw new BadRequestException("maxRating must be ≥ minRating");
        }
        if (req.effectiveTo() != null && req.effectiveTo().isBefore(req.effectiveFrom())) {
            throw new BadRequestException("effectiveTo must be on or after effectiveFrom");
        }

        r.setCode(req.code());
        r.setDescription(req.description());
        r.setMatchRecommendation(req.matchRecommendation());
        r.setMinRating(req.minRating());
        r.setMaxRating(req.maxRating());
        r.setBonusPercent(req.bonusPercent());
        r.setFlatAmount(req.flatAmount());
        r.setCurrency(req.currency() == null ? "AZN" : req.currency().toUpperCase());
        r.setMaxAmount(req.maxAmount());
        r.setPriority(req.priority() == null ? 100 : req.priority());
        r.setEffectiveFrom(req.effectiveFrom());
        r.setEffectiveTo(req.effectiveTo());
        r.setActive(req.active() == null ? true : req.active());
    }
}
