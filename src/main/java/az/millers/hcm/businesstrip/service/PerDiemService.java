package az.millers.hcm.businesstrip.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.businesstrip.domain.PerDiemRule;
import az.millers.hcm.businesstrip.domain.TripType;
import az.millers.hcm.businesstrip.repo.PerDiemRuleRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;

/**
 * Per-diem allowance calculation service (M452).
 */
@Service
public class PerDiemService {

    private final PerDiemRuleRepository rules;
    private final CurrentRequest currentRequest;

    public PerDiemService(PerDiemRuleRepository rules, CurrentRequest currentRequest) {
        this.rules = rules;
        this.currentRequest = currentRequest;
    }

    /**
     * Calculate per-diem breakdown for a trip.
     *
     * @param country destination country
     * @param city destination city (nullable)
     * @param grade employee grade (nullable)
     * @param tripType trip type (nullable)
     * @param days trip duration in days
     * @param date effective date for rule matching
     * @return breakdown with meals, lodging, incidentals, total, matched rule
     */
    @Transactional(readOnly = true)
    public PerDiemBreakdown calculate(String country, String city, String grade,
                                       TripType tripType, int days, LocalDate date) {
        if (country == null || country.isBlank()) {
            throw new BadRequestException("Destination country is required");
        }
        if (days <= 0) {
            throw new BadRequestException("Days must be positive");
        }

        String tenantId = "default";  // Multi-tenant: hardcoded for now
        List<PerDiemRule> matches = rules.findMatchingRules(
                tenantId, country, city, grade, tripType, date);

        if (matches.isEmpty()) {
            // No matching rule — return zero allowance (non-fatal)
            return new PerDiemBreakdown(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, "AZN", null);
        }

        // Most specific rule wins (query orders by specificity)
        PerDiemRule rule = matches.get(0);
        BigDecimal meals = rule.getMealAllowance().multiply(BigDecimal.valueOf(days));
        BigDecimal lodging = rule.getLodgingAllowance().multiply(BigDecimal.valueOf(days));
        BigDecimal incidentals = rule.getIncidentals().multiply(BigDecimal.valueOf(days));
        BigDecimal total = meals.add(lodging).add(incidentals);

        return new PerDiemBreakdown(meals, lodging, incidentals, total,
                rule.getCurrency(), rule.getId());
    }

    // ── CRUD for per-diem rules (HR_ADMIN) ──────────────────────────────────

    @Transactional(readOnly = true)
    public List<PerDiemRule> listActive() {
        String tenantId = "default";  // Multi-tenant: hardcoded for now
        return rules.findByTenantIdAndActiveOrderByDestinationCountryAscDestinationCityAsc(
                tenantId, true);
    }

    @Transactional(readOnly = true)
    public PerDiemRule get(UUID id) {
        String tenantId = "default";  // Multi-tenant: hardcoded for now
        PerDiemRule rule = rules.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Per-diem rule not found: " + id));
        return rule;
    }

    @Transactional
    public PerDiemRule create(PerDiemRule rule) {
        rule.setTenantId("default");  // Multi-tenant: hardcoded for now
        return rules.save(rule);
    }

    @Transactional
    public PerDiemRule update(UUID id, PerDiemRule updated) {
        PerDiemRule existing = get(id);
        existing.setDestinationCountry(updated.getDestinationCountry());
        existing.setDestinationCity(updated.getDestinationCity());
        existing.setEmployeeGrade(updated.getEmployeeGrade());
        existing.setTripType(updated.getTripType());
        existing.setMealAllowance(updated.getMealAllowance());
        existing.setLodgingAllowance(updated.getLodgingAllowance());
        existing.setIncidentals(updated.getIncidentals());
        existing.setCurrency(updated.getCurrency());
        existing.setEffectiveFrom(updated.getEffectiveFrom());
        existing.setEffectiveTo(updated.getEffectiveTo());
        existing.setActive(updated.isActive());
        return rules.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        PerDiemRule rule = get(id);
        rule.setActive(false);
        rules.save(rule);
    }

    /**
     * Per-diem calculation result.
     */
    public record PerDiemBreakdown(
            BigDecimal meals,
            BigDecimal lodging,
            BigDecimal incidentals,
            BigDecimal total,
            String currency,
            UUID matchedRuleId) {
    }
}
