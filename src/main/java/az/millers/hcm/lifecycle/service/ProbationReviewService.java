package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.lifecycle.api.dto.CompleteProbationReviewRequest;
import az.millers.hcm.lifecycle.api.dto.ProbationReviewResponse;
import az.millers.hcm.lifecycle.api.dto.ScheduleProbationReviewRequest;
import az.millers.hcm.lifecycle.domain.EmploymentContract;
import az.millers.hcm.lifecycle.domain.ProbationOutcome;
import az.millers.hcm.lifecycle.domain.ProbationReview;
import az.millers.hcm.lifecycle.domain.ProbationReviewStatus;
import az.millers.hcm.lifecycle.domain.ProbationReviewType;
import az.millers.hcm.lifecycle.repo.EmploymentContractRepository;
import az.millers.hcm.lifecycle.repo.ProbationReviewRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * Manages {@link ProbationReview} milestones (M73 / P2-01).
 *
 * <p>The service has three integration touch points:
 *
 * <ul>
 *   <li>{@link #autoScheduleForContract(EmploymentContract)} — called by
 *       {@code EmploymentContractService.activate()} once the milestone is
 *       wired. Creates MID_POINT + FINAL rows whose scheduled dates are
 *       derived from the contract's probation_end_date.</li>
 *   <li>{@link #cancelForContract(UUID, String)} — called when probation is
 *       cut short (termination, contract rescind). Flips outstanding rows
 *       to CANCELLED so the scheduler stops alerting on them.</li>
 *   <li>{@link #complete(UUID, CompleteProbationReviewRequest)} — records
 *       the manager + HR feedback, marks COMPLETED with an outcome. A
 *       follow-up workflow (PROBATION_PASS → confirm employment, FAIL →
 *       termination, EXTENDED → contract.probation_end_date push) is a
 *       Phase-2 follow-on; M73 captures the decision itself.</li>
 * </ul>
 */
@Service
public class ProbationReviewService {

    private static final String MODULE = "LIFECYCLE";
    private static final String ENTITY = "ProbationReview";

    private final ProbationReviewRepository repository;
    private final EmploymentContractRepository contracts;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;

    public ProbationReviewService(ProbationReviewRepository repository,
                                   EmploymentContractRepository contracts,
                                   EmployeeRepository employees,
                                   AuditService audit,
                                   AccessScopeService accessScope,
                                   CurrentRequest currentRequest) {
        this.repository = repository;
        this.contracts = contracts;
        this.employees = employees;
        this.audit = audit;
        this.accessScope = accessScope;
        this.currentRequest = currentRequest;
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProbationReviewResponse> listForEmployee(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        return repository.findByEmployeeIdOrderByScheduledDateDesc(employeeId)
                .stream().map(ProbationReviewResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProbationReviewResponse get(UUID id) {
        ProbationReview r = loadOrThrow(id);
        ensureEmployeeAccessible(r.getEmployeeId());
        return ProbationReviewResponse.from(r);
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Auto-schedule MID_POINT + FINAL reviews when a contract with a
     * probation period is activated. Called from {@code EmploymentContractService.activate()}.
     *
     * <p>MID_POINT lands roughly half-way between start_date and probation_end_date.
     * FINAL lands 5 calendar days before probation_end_date so HR has time to
     * confirm / extend / terminate before the deadline.
     *
     * <p>Returns an empty list when the contract has no probation period.
     * Idempotent — re-running on the same contract is rejected by the
     * partial unique index (uq_pr_contract_type) and silently caught here.
     */
    @Transactional
    public List<ProbationReviewResponse> autoScheduleForContract(EmploymentContract contract) {
        if (contract == null || contract.getProbationEndDate() == null) {
            return List.of();
        }
        Employee employee = employees.findById(contract.getEmployeeId()).orElse(null);
        if (employee == null) {
            return List.of();
        }

        LocalDate start = contract.getStartDate();
        LocalDate probationEnd = contract.getProbationEndDate();
        long days = ChronoUnit.DAYS.between(start, probationEnd);
        if (days < 14) {
            // Probation too short for two reviews — schedule only the FINAL
            // (the day before probation ends, or today if it's already past).
            return List.of(scheduleIfMissing(contract, employee,
                    ProbationReviewType.FINAL, latest(today(), probationEnd.minusDays(1))));
        }

        LocalDate midPoint = start.plusDays(days / 2);
        LocalDate finalReview = probationEnd.minusDays(5);
        // If both fall on the same day (very short probation), just schedule FINAL.
        if (!midPoint.isBefore(finalReview)) {
            return List.of(scheduleIfMissing(contract, employee,
                    ProbationReviewType.FINAL, finalReview));
        }
        return List.of(
                scheduleIfMissing(contract, employee, ProbationReviewType.MID_POINT, midPoint),
                scheduleIfMissing(contract, employee, ProbationReviewType.FINAL, finalReview)
        ).stream().filter(java.util.Objects::nonNull).toList();
    }

    /**
     * Manual scheduling — for ad-hoc reviews HR wants to add (e.g. after an
     * EXTENDED outcome bumps probation_end_date forward).
     */
    @Transactional
    public ProbationReviewResponse schedule(ScheduleProbationReviewRequest req) {
        EmploymentContract contract = contracts.findById(req.contractId()).orElseThrow(() ->
                new BadRequestException("Contract not found: " + req.contractId()));
        Employee employee = employees.findById(contract.getEmployeeId()).orElseThrow(() ->
                new BadRequestException("Employee not found: " + contract.getEmployeeId()));
        ensureEmployeeAccessible(employee.getId());

        ProbationReview existing = repository.findByContractId(req.contractId()).stream()
                .filter(r -> r.getReviewType() == req.reviewType())
                .findFirst()
                .orElse(null);
        if (existing != null) {
            throw new BadRequestException(
                    "A " + req.reviewType() + " review for contract " + req.contractId()
                            + " already exists (status=" + existing.getStatus() + ")");
        }

        ProbationReview r = new ProbationReview();
        r.setEmployeeId(employee.getId());
        r.setContractId(req.contractId());
        r.setReviewType(req.reviewType());
        r.setScheduledDate(req.scheduledDate());
        r.setManagerEmployeeId(req.managerEmployeeId() != null
                ? req.managerEmployeeId() : employee.getManagerId());
        r.setReviewerEmployeeId(req.reviewerEmployeeId());
        r.setNotes(req.notes());
        r.setStatus(ProbationReviewStatus.SCHEDULED);
        r.setCreatedBy(currentRequest.username());
        r.setUpdatedBy(currentRequest.username());
        ProbationReview saved = repository.save(r);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "SCHEDULE", null, ProbationReviewResponse.from(saved));
        return ProbationReviewResponse.from(saved);
    }

    /**
     * Record the completion of a scheduled review. Manager + HR feedback,
     * outcome, optional effective date. Status flips to COMPLETED.
     */
    @Transactional
    public ProbationReviewResponse complete(UUID id, CompleteProbationReviewRequest req) {
        ProbationReview r = loadOrThrow(id);
        if (r.getStatus() != ProbationReviewStatus.SCHEDULED) {
            throw new BadRequestException(
                    "Only SCHEDULED reviews can be completed — current status: " + r.getStatus());
        }
        ProbationReviewResponse before = ProbationReviewResponse.from(r);

        r.setStatus(ProbationReviewStatus.COMPLETED);
        r.setOutcome(req.outcome());
        r.setCompletedDate(req.completedDate());
        r.setEffectiveDate(req.effectiveDate());
        if (req.managerFeedback() != null) r.setManagerFeedback(req.managerFeedback());
        if (req.managerRating() != null)   r.setManagerRating(req.managerRating());
        if (req.hrFeedback() != null)      r.setHrFeedback(req.hrFeedback());
        if (req.hrRating() != null)        r.setHrRating(req.hrRating());
        if (req.notes() != null && !req.notes().isBlank()) {
            String prefix = r.getNotes() == null ? "" : r.getNotes() + "\n---\n";
            r.setNotes(prefix + req.notes());
        }
        r.setUpdatedBy(currentRequest.username());
        ProbationReview saved = repository.save(r);
        audit.record(MODULE, ENTITY, id.toString(),
                "COMPLETE_" + req.outcome(), before,
                ProbationReviewResponse.from(saved));
        return ProbationReviewResponse.from(saved);
    }

    /**
     * Cancel every outstanding review for the contract — used when probation
     * is cut short (termination, contract rescind).
     */
    @Transactional
    public int cancelForContract(UUID contractId, String reason) {
        List<ProbationReview> outstanding =
                repository.findByContractIdAndStatus(contractId, ProbationReviewStatus.SCHEDULED);
        for (ProbationReview r : outstanding) {
            r.setStatus(ProbationReviewStatus.CANCELLED);
            r.setUpdatedBy(currentRequest.username());
            String prefix = r.getNotes() == null ? "" : r.getNotes() + "\n---\n";
            r.setNotes(prefix + "Cancelled: " + (reason == null ? "(no reason supplied)" : reason));
            repository.save(r);
            audit.record(MODULE, ENTITY, r.getId().toString(),
                    "CANCEL", null,
                    Map.of("reason", reason == null ? "" : reason));
        }
        return outstanding.size();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Insert if a row for ({@code contract}, {@code type}) doesn't yet exist;
     * returns the created row, or null if one was already present.
     */
    private ProbationReviewResponse scheduleIfMissing(EmploymentContract contract,
                                                      Employee employee,
                                                      ProbationReviewType type,
                                                      LocalDate scheduledDate) {
        Optional<ProbationReview> existing = repository.findByContractId(contract.getId())
                .stream()
                .filter(r -> r.getReviewType() == type)
                .findFirst();
        if (existing.isPresent()) {
            return ProbationReviewResponse.from(existing.get());
        }
        ProbationReview r = new ProbationReview();
        r.setEmployeeId(employee.getId());
        r.setContractId(contract.getId());
        r.setReviewType(type);
        r.setScheduledDate(scheduledDate);
        r.setManagerEmployeeId(employee.getManagerId());
        r.setStatus(ProbationReviewStatus.SCHEDULED);
        r.setCreatedBy(currentRequest.username());
        r.setUpdatedBy(currentRequest.username());
        ProbationReview saved = repository.save(r);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "AUTO_SCHEDULE", null, ProbationReviewResponse.from(saved));
        return ProbationReviewResponse.from(saved);
    }

    private LocalDate today() {
        return LocalDate.now();
    }

    private LocalDate latest(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private ProbationReview loadOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Probation review not found: " + id));
    }

    private void ensureEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }
}
