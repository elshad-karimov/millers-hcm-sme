package az.millers.hcm.corehr.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.ApprovalLimitType;
import az.millers.hcm.corehr.domain.EmployeeApprovalLimit;
import az.millers.hcm.corehr.repo.EmployeeApprovalLimitRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M261 — Employee approval limit service (PRD §27).
 *
 * <p>Consumed by:
 * <ul>
 *   <li>{@code EmployeeApprovalLimitController} for direct manual CRUD;
 *   <li>{@code PositionProfileGrantService} for auto-grant on hire and
 *       effective-date-out on revoke.
 * </ul>
 */
@Service
public class EmployeeApprovalLimitService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeApprovalLimit";

    private final EmployeeApprovalLimitRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public EmployeeApprovalLimitService(EmployeeApprovalLimitRepository repo,
                                         AuditService audit,
                                         CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<EmployeeApprovalLimit> listForEmployee(UUID employeeId) {
        return repo.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public List<EmployeeApprovalLimit> listActive(UUID employeeId) {
        return repo.findByEmployeeIdAndEffectiveToIsNullOrderByLimitTypeAsc(employeeId);
    }

    /**
     * Create a limit row. When {@code source = PROFILE_GRANT}, sourceGrantId
     * should be the grant UUID — that lets the auto-revoke path find this
     * row on revoke without scanning.
     */
    @Transactional
    public EmployeeApprovalLimit assign(UUID employeeId,
                                          ApprovalLimitType type,
                                          BigDecimal maxAmount,
                                          String currency,
                                          LocalDate effectiveFrom,
                                          String source,
                                          UUID sourceGrantId,
                                          String notes) {
        if (maxAmount == null || maxAmount.signum() < 0) {
            throw new BadRequestException("maxAmount must be >= 0");
        }
        EmployeeApprovalLimit l = new EmployeeApprovalLimit();
        l.setEmployeeId(employeeId);
        l.setLimitType(type);
        l.setMaxAmount(maxAmount);
        l.setCurrency(currency == null || currency.isBlank() ? "AZN" : currency);
        l.setEffectiveFrom(effectiveFrom == null ? LocalDate.now() : effectiveFrom);
        l.setSource(source == null ? "MANUAL" : source);
        l.setSourceGrantId(sourceGrantId);
        l.setNotes(notes);
        l.setCreatedBy(currentRequest.username());
        l.setUpdatedBy(currentRequest.username());
        EmployeeApprovalLimit saved = repo.save(l);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, summarise(saved));
        return saved;
    }

    /**
     * Effective-date-out the limit. Returns empty if {@code limitId} doesn't
     * exist — callers (e.g. auto-revoke) shouldn't crash on a stale id.
     */
    @Transactional
    public Optional<EmployeeApprovalLimit> end(UUID limitId, LocalDate endDate, String notes) {
        return repo.findById(limitId).map(l -> {
            if (l.getEffectiveTo() != null) return l;  // already ended
            l.setEffectiveTo(endDate == null ? LocalDate.now() : endDate);
            if (notes != null && !notes.isBlank()) {
                l.setNotes((l.getNotes() == null ? "" : l.getNotes() + "\n") + notes);
            }
            l.setUpdatedBy(currentRequest.username());
            EmployeeApprovalLimit saved = repo.save(l);
            audit.record(MODULE, ENTITY, saved.getId().toString(),
                    "END", null, summarise(saved));
            return saved;
        });
    }

    private java.util.Map<String, Object> summarise(EmployeeApprovalLimit l) {
        return java.util.Map.of(
                "employeeId", l.getEmployeeId(),
                "limitType", l.getLimitType(),
                "maxAmount", l.getMaxAmount(),
                "currency", l.getCurrency(),
                "effectiveFrom", l.getEffectiveFrom(),
                "effectiveTo", l.getEffectiveTo() == null ? "" : l.getEffectiveTo(),
                "source", l.getSource(),
                "sourceGrantId", l.getSourceGrantId() == null ? "" : l.getSourceGrantId());
    }
}
