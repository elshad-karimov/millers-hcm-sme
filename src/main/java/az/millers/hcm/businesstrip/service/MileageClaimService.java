package az.millers.hcm.businesstrip.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.businesstrip.domain.MileageClaim;
import az.millers.hcm.businesstrip.domain.MileageClaimStatus;
import az.millers.hcm.businesstrip.domain.VehicleType;
import az.millers.hcm.businesstrip.repo.MileageClaimRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.common.BusinessNumbers;

/**
 * Mileage claim lifecycle (M453).
 * DRAFT → SUBMITTED (employee) → APPROVED/REJECTED (manager or HR) → PAID (HR).
 */
@Service
public class MileageClaimService {

    private static final String MODULE = "BUSINESS_TRIP";
    private static final String ENTITY = "MileageClaim";

    private final MileageClaimRepository claims;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final AccessScopeService accessScope;
    private final az.millers.hcm.selfservice.service.EmployeeContextService employeeContext;

    public MileageClaimService(MileageClaimRepository claims,
                                AuditService audit,
                                CurrentRequest currentRequest,
                                AccessScopeService accessScope,
                                az.millers.hcm.selfservice.service.EmployeeContextService employeeContext) {
        this.claims = claims;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.accessScope = accessScope;
        this.employeeContext = employeeContext;
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    @Transactional
    public MileageClaim submit(UUID employeeId, LocalDate claimDate, VehicleType vehicleType,
                                String startLocation, String endLocation, BigDecimal distanceKm,
                                BigDecimal ratePerKm, String notes) {
        if (distanceKm == null || distanceKm.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Distance must be positive");
        }

        MileageClaim claim = new MileageClaim();
        claim.setTenantId("default");
        claim.setClaimNo(BusinessNumbers.format("MC", 5, claims.nextClaimNoSequence()));
        claim.setEmployeeId(employeeId);
        claim.setClaimDate(claimDate != null ? claimDate : LocalDate.now());
        claim.setVehicleType(vehicleType);
        claim.setStartLocation(startLocation);
        claim.setEndLocation(endLocation);
        claim.setDistanceKm(distanceKm);
        claim.setRatePerKm(ratePerKm != null ? ratePerKm : new BigDecimal("0.30"));
        claim.setTotalAmount(distanceKm.multiply(claim.getRatePerKm())
                .setScale(2, java.math.RoundingMode.HALF_UP));
        claim.setNotes(notes);
        claim.setStatus(MileageClaimStatus.SUBMITTED);
        claim.setCreatedBy(currentRequest.username());

        MileageClaim saved = claims.save(claim);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "SUBMIT", null,
                Map.of("claimNo", saved.getClaimNo(), "distance", distanceKm.toString(),
                        "total", saved.getTotalAmount().toString()));
        return saved;
    }

    @Transactional
    public MileageClaim approve(UUID claimId) {
        MileageClaim claim = get(claimId);
        if (claim.getStatus() != MileageClaimStatus.SUBMITTED) {
            throw new BadRequestException("Only SUBMITTED claims can be approved");
        }

        // Self-approve guard
        try {
            UUID currentEmployeeId = employeeContext.currentEmployee().getId();
            if (claim.getEmployeeId().equals(currentEmployeeId)) {
                throw new BadRequestException("You cannot approve your own claim");
            }
        } catch (Exception e) {
            // If currentEmployee() fails (non-employee user), proceed to scope check
        }

        // AccessScopeService enforces manager-or-HR check (employee's manager or HR role)
        if (!accessScope.isAccessible(claim.getEmployeeId()) && !currentRequest.hasRole("HR")) {
            throw new BadRequestException("You can only approve claims for your team");
        }

        claim.setStatus(MileageClaimStatus.APPROVED);
        claim.setApprovedBy(currentRequest.username());
        claim.setApprovedAt(OffsetDateTime.now());
        MileageClaim saved = claims.save(claim);
        audit.record(MODULE, ENTITY, claimId.toString(), "APPROVE", null, Map.of());
        return saved;
    }

    @Transactional
    public MileageClaim reject(UUID claimId, String reason) {
        MileageClaim claim = get(claimId);
        if (claim.getStatus() != MileageClaimStatus.SUBMITTED) {
            throw new BadRequestException("Only SUBMITTED claims can be rejected");
        }

        // Self-reject guard (consistency with approve)
        try {
            UUID currentEmployeeId = employeeContext.currentEmployee().getId();
            if (claim.getEmployeeId().equals(currentEmployeeId)) {
                throw new BadRequestException("You cannot reject your own claim (use cancel instead)");
            }
        } catch (Exception e) {
            // If currentEmployee() fails (non-employee user), proceed to scope check
        }

        if (!accessScope.isAccessible(claim.getEmployeeId()) && !currentRequest.hasRole("HR")) {
            throw new BadRequestException("You can only reject claims for your team");
        }

        claim.setStatus(MileageClaimStatus.REJECTED);
        claim.setRejectedAt(OffsetDateTime.now());
        claim.setRejectionReason(reason);
        MileageClaim saved = claims.save(claim);
        audit.record(MODULE, ENTITY, claimId.toString(), "REJECT", null,
                Map.of("reason", reason != null ? reason : ""));
        return saved;
    }

    @Transactional
    public MileageClaim markPaid(UUID claimId) {
        MileageClaim claim = get(claimId);
        if (claim.getStatus() != MileageClaimStatus.APPROVED) {
            throw new BadRequestException("Only APPROVED claims can be marked paid");
        }
        claim.setStatus(MileageClaimStatus.PAID);
        claim.setPaidAt(OffsetDateTime.now());
        MileageClaim saved = claims.save(claim);
        audit.record(MODULE, ENTITY, claimId.toString(), "PAID", null, Map.of());
        return saved;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MileageClaim get(UUID claimId) {
        String tenantId = "default";
        MileageClaim claim = claims.findByIdAndTenantId(claimId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Mileage claim not found: " + claimId));
        // ABAC: hide rows the caller isn't scoped to
        if (!accessScope.isAccessible(claim.getEmployeeId())) {
            throw new ResourceNotFoundException("Mileage claim not found: " + claimId);
        }
        return claim;
    }

    @Transactional(readOnly = true)
    public List<MileageClaim> forEmployee(UUID employeeId) {
        String tenantId = "default";

        // IDOR guard: enforce access scope (employee = self only; manager = team; HR = all)
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("You do not have access to this employee's claims");
        }

        return claims.findByTenantIdAndEmployeeIdOrderByClaimDateDesc(tenantId, employeeId);
    }

    @Transactional(readOnly = true)
    public List<MileageClaim> list(MileageClaimStatus status) {
        String tenantId = "default";
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope == null) {
            // HR: see all
            return status != null
                    ? claims.findByTenantIdAndStatusOrderByClaimDateDesc(tenantId, status)
                    : claims.findAll();
        }
        if (scope.isEmpty()) return List.of();
        // Manager: see team claims
        return status != null
                ? claims.findByTenantIdAndEmployeeIdInAndStatusOrderByClaimDateDesc(tenantId, scope, status)
                : claims.findByTenantIdAndEmployeeIdInOrderByClaimDateDesc(tenantId, scope);
    }
}
