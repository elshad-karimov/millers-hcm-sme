package az.millers.hcm.payroll.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.payroll.domain.PayrollRunHold;
import az.millers.hcm.payroll.repo.PayrollRunHoldRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class PayrollHoldService {

    private static final String MODULE = "PAYROLL";
    private static final String ENTITY = "PayrollRunHold";

    private final PayrollRunHoldRepository holds;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PayrollHoldService(PayrollRunHoldRepository holds,
                              AuditService audit,
                              CurrentRequest currentRequest) {
        this.holds = holds;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public PayrollRunHold setHold(UUID runId, UUID employeeId, String reason) {
        // Check if already held
        PayrollRunHold existing = holds.findByRunIdAndEmployeeId(runId, employeeId).orElse(null);

        if (existing != null && existing.getReleasedAt() == null) {
            // Already on hold, just update the reason
            existing.setReason(reason);
            PayrollRunHold saved = holds.save(existing);
            audit.record(MODULE, ENTITY, saved.getId().toString(),
                    "HOLD_UPDATED", null,
                    Map.of("runId", runId.toString(),
                           "employeeId", employeeId.toString(),
                           "reason", reason == null ? "" : reason));
            return saved;
        }

        if (existing != null) {
            // Previously released, reactivate
            existing.setReleasedAt(null);
            existing.setReason(reason);
            existing.setHeldBy(currentRequest.username());
            PayrollRunHold saved = holds.save(existing);
            audit.record(MODULE, ENTITY, saved.getId().toString(),
                    "HOLD_SET", null,
                    Map.of("runId", runId.toString(),
                           "employeeId", employeeId.toString(),
                           "reason", reason == null ? "" : reason));
            return saved;
        }

        // Create new hold
        PayrollRunHold hold = new PayrollRunHold();
        hold.setRunId(runId);
        hold.setEmployeeId(employeeId);
        hold.setReason(reason);
        hold.setHeldBy(currentRequest.username());
        PayrollRunHold saved = holds.save(hold);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "HOLD_SET", null,
                Map.of("runId", runId.toString(),
                       "employeeId", employeeId.toString(),
                       "reason", reason == null ? "" : reason));
        return saved;
    }

    @Transactional
    public void releaseHold(UUID runId, UUID employeeId) {
        PayrollRunHold hold = holds.findByRunIdAndEmployeeId(runId, employeeId).orElse(null);
        if (hold != null && hold.getReleasedAt() == null) {
            hold.setReleasedAt(OffsetDateTime.now());
            holds.save(hold);
            audit.record(MODULE, ENTITY, hold.getId().toString(),
                    "HOLD_RELEASED", null,
                    Map.of("runId", runId.toString(),
                           "employeeId", employeeId.toString()));
        }
    }

    @Transactional(readOnly = true)
    public Set<UUID> heldEmployeeIds(UUID runId) {
        return holds.findByRunId(runId).stream()
                .filter(h -> h.getReleasedAt() == null)
                .map(PayrollRunHold::getEmployeeId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<PayrollRunHold> getHolds(UUID runId) {
        return holds.findByRunId(runId);
    }
}
