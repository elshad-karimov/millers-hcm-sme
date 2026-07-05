package az.millers.hcm.businesstrip.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.businesstrip.domain.MileageClaim;
import az.millers.hcm.businesstrip.domain.MileageClaimStatus;
import az.millers.hcm.businesstrip.domain.VehicleType;
import az.millers.hcm.businesstrip.service.MileageClaimService;

/**
 * Mileage claims (M453).
 */
@RestController
@RequestMapping("/api/business-trips/mileage-claims")
public class MileageClaimController {

    private final MileageClaimService service;

    public MileageClaimController(MileageClaimService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SELF_SERVICE')")
    public MileageClaimResponse submit(@RequestBody MileageClaimRequest req) {
        MileageClaim claim = service.submit(req.employeeId(), req.claimDate(),
                req.vehicleType(), req.startLocation(), req.endLocation(),
                req.distanceKm(), req.ratePerKm(), req.notes());
        return toResponse(claim);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('WRITE_HR')")
    public MileageClaimResponse approve(@PathVariable UUID id) {
        return toResponse(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('WRITE_HR')")
    public MileageClaimResponse reject(@PathVariable UUID id, @RequestBody(required = false) RejectRequest req) {
        return toResponse(service.reject(id, req != null ? req.reason() : null));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('WRITE_HR')")
    public MileageClaimResponse markPaid(@PathVariable UUID id) {
        return toResponse(service.markPaid(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ_HR')")
    public MileageClaimResponse get(@PathVariable UUID id) {
        return toResponse(service.get(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('READ_HR')")
    public List<MileageClaimResponse> list(@RequestParam(required = false) MileageClaimStatus status) {
        return service.list(status).stream().map(this::toResponse).toList();
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("hasAuthority('SELF_SERVICE')")
    public List<MileageClaimResponse> forEmployee(@PathVariable UUID employeeId) {
        return service.forEmployee(employeeId).stream().map(this::toResponse).toList();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    private MileageClaimResponse toResponse(MileageClaim c) {
        return new MileageClaimResponse(
                c.getId(), c.getClaimNo(), c.getEmployeeId(), c.getClaimDate(),
                c.getVehicleType(), c.getStartLocation(), c.getEndLocation(),
                c.getDistanceKm(), c.getRatePerKm(), c.getTotalAmount(), c.getCurrency(),
                c.getStatus(), c.getApprovedBy(), c.getApprovedAt(),
                c.getRejectedAt(), c.getRejectionReason(), c.getPaidAt(),
                c.getNotes(), c.getCreatedAt());
    }

    public record MileageClaimRequest(
            UUID employeeId,
            LocalDate claimDate,
            VehicleType vehicleType,
            String startLocation,
            String endLocation,
            BigDecimal distanceKm,
            BigDecimal ratePerKm,
            String notes) {
    }

    public record MileageClaimResponse(
            UUID id,
            String claimNo,
            UUID employeeId,
            LocalDate claimDate,
            VehicleType vehicleType,
            String startLocation,
            String endLocation,
            BigDecimal distanceKm,
            BigDecimal ratePerKm,
            BigDecimal totalAmount,
            String currency,
            MileageClaimStatus status,
            String approvedBy,
            OffsetDateTime approvedAt,
            OffsetDateTime rejectedAt,
            String rejectionReason,
            OffsetDateTime paidAt,
            String notes,
            OffsetDateTime createdAt) {
    }

    public record RejectRequest(String reason) {
    }
}
