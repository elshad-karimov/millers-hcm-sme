package az.millers.hcm.businesstrip.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.businesstrip.domain.PerDiemRule;
import az.millers.hcm.businesstrip.domain.TripType;
import az.millers.hcm.businesstrip.service.PerDiemService;
import az.millers.hcm.businesstrip.service.PerDiemService.PerDiemBreakdown;

/**
 * Per-diem allowance rules & calculation (M452).
 */
@RestController
@RequestMapping("/api/business-trips/per-diem")
public class PerDiemController {

    private final PerDiemService service;

    public PerDiemController(PerDiemService service) {
        this.service = service;
    }

    /**
     * Preview per-diem calculation for a trip (called by trip form).
     */
    @GetMapping("/preview")
    @PreAuthorize("hasAnyAuthority('READ_HR', 'SELF_SERVICE')")
    public PerDiemBreakdownResponse preview(
            @RequestParam String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) TripType tripType,
            @RequestParam int days,
            @RequestParam(required = false) LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        PerDiemBreakdown breakdown = service.calculate(country, city, grade, tripType, days, effectiveDate);
        return new PerDiemBreakdownResponse(
                breakdown.meals(), breakdown.lodging(), breakdown.incidentals(),
                breakdown.total(), breakdown.currency(), breakdown.matchedRuleId());
    }

    // ── Per-diem rules CRUD (HR_ADMIN) ──────────────────────────────────────

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('READ_HR')")
    public List<PerDiemRuleResponse> listRules() {
        return service.listActive().stream().map(this::toResponse).toList();
    }

    @GetMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('READ_HR')")
    public PerDiemRuleResponse getRule(@PathVariable UUID id) {
        return toResponse(service.get(id));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('WRITE_HR_ADMIN')")
    public PerDiemRuleResponse createRule(@RequestBody PerDiemRuleRequest req) {
        PerDiemRule rule = new PerDiemRule();
        applyRequest(rule, req);
        return toResponse(service.create(rule));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('WRITE_HR_ADMIN')")
    public PerDiemRuleResponse updateRule(@PathVariable UUID id, @RequestBody PerDiemRuleRequest req) {
        PerDiemRule updated = new PerDiemRule();
        applyRequest(updated, req);
        return toResponse(service.update(id, updated));
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('WRITE_HR_ADMIN')")
    public void deleteRule(@PathVariable UUID id) {
        service.delete(id);
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    private void applyRequest(PerDiemRule rule, PerDiemRuleRequest req) {
        rule.setDestinationCountry(req.destinationCountry());
        rule.setDestinationCity(req.destinationCity());
        rule.setEmployeeGrade(req.employeeGrade());
        rule.setTripType(req.tripType());
        rule.setMealAllowance(req.mealAllowance());
        rule.setLodgingAllowance(req.lodgingAllowance());
        rule.setIncidentals(req.incidentals());
        rule.setCurrency(req.currency() != null ? req.currency() : "AZN");
        rule.setEffectiveFrom(req.effectiveFrom());
        rule.setEffectiveTo(req.effectiveTo());
        rule.setActive(req.active() != null ? req.active() : true);
    }

    private PerDiemRuleResponse toResponse(PerDiemRule r) {
        return new PerDiemRuleResponse(
                r.getId(), r.getDestinationCountry(), r.getDestinationCity(),
                r.getEmployeeGrade(), r.getTripType(),
                r.getMealAllowance(), r.getLodgingAllowance(), r.getIncidentals(),
                r.getCurrency(), r.getEffectiveFrom(), r.getEffectiveTo(),
                r.isActive(), r.getCreatedAt(), r.getUpdatedAt());
    }

    public record PerDiemBreakdownResponse(
            BigDecimal meals,
            BigDecimal lodging,
            BigDecimal incidentals,
            BigDecimal total,
            String currency,
            UUID matchedRuleId) {
    }

    public record PerDiemRuleRequest(
            String destinationCountry,
            String destinationCity,
            String employeeGrade,
            TripType tripType,
            BigDecimal mealAllowance,
            BigDecimal lodgingAllowance,
            BigDecimal incidentals,
            String currency,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Boolean active) {
    }

    public record PerDiemRuleResponse(
            UUID id,
            String destinationCountry,
            String destinationCity,
            String employeeGrade,
            TripType tripType,
            BigDecimal mealAllowance,
            BigDecimal lodgingAllowance,
            BigDecimal incidentals,
            String currency,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }
}
