package az.millers.hcm.corehr.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.domain.ApprovalLimitType;
import az.millers.hcm.corehr.domain.EmployeeApprovalLimit;
import az.millers.hcm.corehr.service.EmployeeApprovalLimitService;
import az.millers.hcm.security.SecurityRoles;

/** M261 — Employee approval limit REST (PRD §27). */
@RestController
@RequestMapping("/api/employees/{employeeId}/approval-limits")
public class EmployeeApprovalLimitController {

    private final EmployeeApprovalLimitService service;

    public EmployeeApprovalLimitController(EmployeeApprovalLimitService service) {
        this.service = service;
    }

    public record LimitResponse(
            UUID id,
            UUID employeeId,
            ApprovalLimitType limitType,
            BigDecimal maxAmount,
            String currency,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String source,
            UUID sourceGrantId,
            String notes,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy) {

        public static LimitResponse from(EmployeeApprovalLimit l) {
            return new LimitResponse(
                    l.getId(), l.getEmployeeId(), l.getLimitType(),
                    l.getMaxAmount(), l.getCurrency(),
                    l.getEffectiveFrom(), l.getEffectiveTo(),
                    l.getSource(), l.getSourceGrantId(),
                    l.getNotes(),
                    l.getCreatedAt(), l.getCreatedBy(),
                    l.getUpdatedAt(), l.getUpdatedBy());
        }
    }

    public record AssignRequest(
            @NotNull ApprovalLimitType limitType,
            @NotNull @PositiveOrZero BigDecimal maxAmount,
            @Size(min = 3, max = 3) String currency,
            LocalDate effectiveFrom,
            @Size(max = 2000) String notes) {}

    public record EndRequest(LocalDate endDate, @Size(max = 2000) String notes) {}

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<LimitResponse> list(@PathVariable UUID employeeId) {
        return service.listForEmployee(employeeId).stream()
                .map(LimitResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LimitResponse assign(@PathVariable UUID employeeId,
                                 @Valid @RequestBody AssignRequest req) {
        return LimitResponse.from(service.assign(
                employeeId, req.limitType(), req.maxAmount(),
                req.currency(), req.effectiveFrom(),
                "MANUAL", null, req.notes()));
    }

    @DeleteMapping("/{limitId}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LimitResponse end(@PathVariable UUID employeeId,
                              @PathVariable UUID limitId,
                              @RequestBody(required = false) EndRequest req) {
        return service.end(limitId,
                        req == null ? null : req.endDate(),
                        req == null ? null : req.notes())
                .map(LimitResponse::from)
                .orElseThrow(() -> new az.millers.hcm.common.ResourceNotFoundException(
                        "Limit not found: " + limitId));
    }
}
