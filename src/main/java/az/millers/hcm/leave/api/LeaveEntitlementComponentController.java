package az.millers.hcm.leave.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.leave.api.dto.EntitlementComponentDtos.BreakdownResponse;
import az.millers.hcm.leave.api.dto.EntitlementComponentDtos.ComponentResponse;
import az.millers.hcm.leave.api.dto.EntitlementComponentDtos.ManualComponentRequest;
import az.millers.hcm.leave.domain.LeaveEntitlementComponent;
import az.millers.hcm.leave.service.LeaveEntitlementComponentService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * M151 — the itemised annual leave entitlement behind one balance.
 *
 * <p>Read is open to HR, managers and auditors: the breakdown is what an
 * inspection asks for, and an auditor who cannot see it cannot verify the
 * total. Writing a manual component and forcing a recalculation are HR-only —
 * both move an entitlement, and the audit trail names who did it.
 */
@RestController
public class LeaveEntitlementComponentController {

    private static final String READ_ROLES = SecurityRoles.READ_HR_PLUS_MANAGERS;
    private static final String WRITE_ROLES = SecurityRoles.WRITE_HR;

    private final LeaveEntitlementComponentService service;

    public LeaveEntitlementComponentController(LeaveEntitlementComponentService service) {
        this.service = service;
    }

    @GetMapping("/api/employees/{employeeId}/leave-entitlement/{leaveTypeId}")
    @PreAuthorize(READ_ROLES)
    public BreakdownResponse breakdown(@PathVariable UUID employeeId,
                                        @PathVariable UUID leaveTypeId,
                                        @RequestParam(required = false) Integer year) {
        int y = year == null ? LocalDate.now().getYear() : year;
        return toResponse(employeeId, leaveTypeId, y, service.breakdown(employeeId, leaveTypeId, y));
    }

    /**
     * Re-derive the breakdown from current employee, position and dependent
     * data. Manual components are preserved.
     */
    @PostMapping("/api/employees/{employeeId}/leave-entitlement/{leaveTypeId}/recalculate")
    @PreAuthorize(WRITE_ROLES)
    public BreakdownResponse recalculate(@PathVariable UUID employeeId,
                                          @PathVariable UUID leaveTypeId,
                                          @RequestParam(required = false) Integer year) {
        int y = year == null ? LocalDate.now().getYear() : year;
        return toResponse(employeeId, leaveTypeId, y,
                service.recalculate(employeeId, leaveTypeId, y));
    }

    /** Set or clear a manual component — blood donation and one-off grants. */
    @PutMapping("/api/employees/{employeeId}/leave-entitlement/{leaveTypeId}/manual")
    @PreAuthorize(WRITE_ROLES)
    public BreakdownResponse setManual(@PathVariable UUID employeeId,
                                        @PathVariable UUID leaveTypeId,
                                        @RequestParam(required = false) Integer year,
                                        @RequestBody @Valid ManualComponentRequest req) {
        int y = year == null ? LocalDate.now().getYear() : year;
        return toResponse(employeeId, leaveTypeId, y,
                service.setManual(employeeId, leaveTypeId, y,
                        req.componentCode(), req.days(), req.basis()));
    }

    private static BreakdownResponse toResponse(UUID employeeId, UUID leaveTypeId, int year,
                                                 List<LeaveEntitlementComponent> rows) {
        // Mirrors LeaveEntitlementComponentService.sum() — the figure on screen
        // has to be the one that was written into the balance, so blood-donation
        // days are excluded here too.
        BigDecimal total = rows.stream()
                .filter(c -> c.getComponentCode().countsTowardAnnualEntitlement())
                .map(LeaveEntitlementComponent::getDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BreakdownResponse(employeeId, leaveTypeId, year, total,
                rows.stream()
                        .sorted((a, b) -> a.getComponentCode().compareTo(b.getComponentCode()))
                        .map(ComponentResponse::from)
                        .toList());
    }
}
