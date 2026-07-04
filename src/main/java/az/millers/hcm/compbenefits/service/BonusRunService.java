package az.millers.hcm.compbenefits.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.service.WorkflowService;
import az.millers.hcm.compbenefits.api.dto.BonusRunGenerateRequest;
import az.millers.hcm.compbenefits.api.dto.BonusRunResponse;
import az.millers.hcm.compbenefits.domain.BonusItemSource;
import az.millers.hcm.compbenefits.domain.BonusMatrixRule;
import az.millers.hcm.compbenefits.domain.BonusRun;
import az.millers.hcm.compbenefits.domain.BonusRunItem;
import az.millers.hcm.compbenefits.domain.BonusRunStatus;
import az.millers.hcm.compbenefits.repo.BonusRunItemRepository;
import az.millers.hcm.compbenefits.repo.BonusRunRepository;
import az.millers.hcm.payroll.domain.BonusType;
import az.millers.hcm.payroll.domain.PayrollBonus;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollRunStatus;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;
import az.millers.hcm.payroll.repo.PayrollBonusRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;
import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.domain.ReviewCycle;
import az.millers.hcm.performance.domain.ReviewStatus;
import az.millers.hcm.performance.repo.PerformanceReviewRepository;
import az.millers.hcm.performance.repo.ReviewCycleRepository;
import az.millers.hcm.security.CurrentRequest;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Bonus Run service (PRD 8.15.2 + 8.15.3 / M200).
 *
 * <p>Lifecycle:
 * <pre>
 *   DRAFT → GENERATED → PENDING_APPROVAL → APPROVED → PUSHED
 *   Any non-PUSHED state → CANCELLED
 * </pre>
 * GENERATED items are derived from a Performance cycle via the bonus matrix.
 * On submit(), the run is routed through BONUS_RUN_APPROVAL workflow.
 * On APPROVED, push() can be called to write rows into {@code payroll.payroll_bonus}.
 *
 * <p>Per-item amount derivation, in priority order:
 * <ol>
 *   <li>If {@code review.bonusPercent} is set during calibration →
 *       {@code base × bonusPercent / 100} ({@code REVIEW_OVERRIDE}).</li>
 *   <li>Otherwise look up the bonus matrix by recommendation + finalRating
 *       on the run's period anchor date. Pick the highest-priority rule
 *       that matches. Apply the rule's {@code flat_amount} when set, else
 *       {@code base × bonus_percent / 100}. Cap at {@code max_amount}
 *       ({@code MATRIX_LOOKUP}).</li>
 *   <li>If neither yields a rule, the item is created with zero amount and
 *       a note so admins can fix it manually.</li>
 * </ol>
 */
@Service
public class BonusRunService {

    public static final String WORKFLOW_DEFINITION = "BONUS_RUN_APPROVAL";
    public static final String WORKFLOW_ENTITY = "BonusRun";

    private static final Logger log = LoggerFactory.getLogger(BonusRunService.class);
    private static final String MODULE = "COMP_BENEFITS";

    private final BonusRunRepository runs;
    private final BonusRunItemRepository items;
    private final ReviewCycleRepository cycles;
    private final PerformanceReviewRepository reviews;
    private final EmployeeCompensationRepository compensations;
    private final PayrollRunRepository payrollRuns;
    private final PayrollBonusRepository payrollBonuses;
    private final BonusMatrixService matrix;
    private final WorkflowService workflowService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public BonusRunService(BonusRunRepository runs,
                           BonusRunItemRepository items,
                           ReviewCycleRepository cycles,
                           PerformanceReviewRepository reviews,
                           EmployeeCompensationRepository compensations,
                           PayrollRunRepository payrollRuns,
                           PayrollBonusRepository payrollBonuses,
                           BonusMatrixService matrix,
                           WorkflowService workflowService,
                           AuditService audit,
                           CurrentRequest currentRequest) {
        this.runs = runs;
        this.items = items;
        this.cycles = cycles;
        this.reviews = reviews;
        this.compensations = compensations;
        this.payrollRuns = payrollRuns;
        this.payrollBonuses = payrollBonuses;
        this.matrix = matrix;
        this.workflowService = workflowService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public BonusRun get(UUID id) {
        return runs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bonus run not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<BonusRun> list(UUID cycleId, BonusRunStatus status, Pageable pageable) {
        if (cycleId != null) return runs.findByCycleIdOrderByCreatedAtDesc(cycleId, pageable);
        if (status != null)  return runs.findByStatusOrderByCreatedAtDesc(status, pageable);
        return runs.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<BonusRunItem> itemsFor(UUID runId) {
        return items.findByRunIdOrderByCreatedAt(runId);
    }

    @Transactional
    public BonusRun generate(BonusRunGenerateRequest req) {
        ReviewCycle cycle = cycles.findById(req.cycleId())
                .orElseThrow(() -> new BadRequestException("Cycle not found: " + req.cycleId()));
        if (req.periodMonth() < 1 || req.periodMonth() > 12) {
            throw new BadRequestException("periodMonth must be between 1 and 12");
        }
        String currency = req.currency() == null ? "AZN" : req.currency().toUpperCase();

        // Anchor date for matrix lookup: first day of the bonus run's period.
        LocalDate anchor = LocalDate.of(req.periodYear(), req.periodMonth(), 1);

        // HCM_12 M402 — only FINALISED ratings feed compensation (§24/§37):
        // APPROVED / COMPLETED. CALIBRATING reviews are still moving and would
        // pay out pre-final numbers — excluded.
        List<PerformanceReview> eligible = reviews
                .findByCycleIdOrderByCreatedAtDesc(cycle.getId()).stream()
                .filter(r -> r.getStatus() == ReviewStatus.APPROVED
                        || r.getStatus() == ReviewStatus.COMPLETED)
                .toList();

        BonusRun run = new BonusRun();
        run.setRunNo(String.format("BR-%05d", runs.nextNoSequence()));
        run.setName(req.name());
        run.setCycleId(cycle.getId());
        run.setPeriodYear(req.periodYear());
        run.setPeriodMonth(req.periodMonth());
        run.setCurrency(currency);
        run.setStatus(BonusRunStatus.GENERATED);
        run.setGeneratedAt(OffsetDateTime.now());
        run.setGeneratedBy(currentRequest.username());
        run.setNote(req.note());
        run.setCreatedBy(currentRequest.username());
        BonusRun savedRun = runs.save(run);

        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        Map<String, Object> trace = new LinkedHashMap<>();
        List<Map<String, Object>> perEmployeeTrace = new ArrayList<>();

        for (PerformanceReview r : eligible) {
            BigDecimal base = compensations.findActiveOn(r.getEmployeeId(), anchor)
                    .map(c -> c.getMonthlyBaseSalary())
                    .orElse(null);

            BonusItemSource source;
            BigDecimal bonusPercent = null;
            BigDecimal bonusAmount = BigDecimal.ZERO;
            UUID matrixRuleId = null;
            String note = null;

            if (r.getBonusPercent() != null && r.getBonusPercent().signum() > 0) {
                source = BonusItemSource.REVIEW_OVERRIDE;
                bonusPercent = r.getBonusPercent();
                if (base != null) {
                    bonusAmount = base.multiply(bonusPercent)
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                } else {
                    note = "No base salary on " + anchor + " — review override produced 0";
                }
            } else {
                Optional<BonusMatrixRule> ruleOpt = matrix.lookup(
                        r.getRecommendation(), r.getFinalRating(), anchor);
                if (ruleOpt.isPresent()) {
                    BonusMatrixRule rule = ruleOpt.get();
                    matrixRuleId = rule.getId();
                    source = BonusItemSource.MATRIX_LOOKUP;
                    if (rule.getFlatAmount() != null) {
                        bonusAmount = rule.getFlatAmount();
                    } else if (rule.getBonusPercent() != null) {
                        bonusPercent = rule.getBonusPercent();
                        if (base != null) {
                            bonusAmount = base.multiply(bonusPercent)
                                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        } else {
                            note = "No base salary on " + anchor + " — matrix produced 0";
                        }
                    }
                    if (rule.getMaxAmount() != null
                            && bonusAmount.compareTo(rule.getMaxAmount()) > 0) {
                        note = (note == null ? "" : note + "; ")
                                + "capped at maxAmount " + rule.getMaxAmount();
                        bonusAmount = rule.getMaxAmount();
                    }
                } else {
                    source = BonusItemSource.MATRIX_LOOKUP;
                    note = "No matching matrix rule — manual review required";
                }
            }

            BonusRunItem item = new BonusRunItem();
            item.setItemNo(String.format("BRI-%05d", items.nextItemNoSequence()));
            item.setRunId(savedRun.getId());
            item.setEmployeeId(r.getEmployeeId());
            item.setReviewId(r.getId());
            item.setRecommendation(r.getRecommendation());
            item.setFinalRating(r.getFinalRating());
            item.setBaseSalary(base);
            item.setBonusPercent(bonusPercent);
            item.setBonusAmount(bonusAmount);
            item.setCurrency(currency);
            item.setSource(source);
            item.setMatrixRuleId(matrixRuleId);
            item.setNote(note);
            items.save(item);

            total = total.add(bonusAmount);
            count++;

            Map<String, Object> row = new HashMap<>();
            row.put("employeeId", r.getEmployeeId().toString());
            row.put("reviewNo", r.getReviewNo());
            row.put("recommendation", r.getRecommendation());
            row.put("finalRating", r.getFinalRating() == null ? null : r.getFinalRating().toPlainString());
            row.put("base", base == null ? null : base.toPlainString());
            row.put("bonusPercent", bonusPercent == null ? null : bonusPercent.toPlainString());
            row.put("bonusAmount", bonusAmount.toPlainString());
            row.put("source", source.name());
            if (note != null) row.put("note", note);
            perEmployeeTrace.add(row);
        }

        savedRun.setTotalAmount(total);
        savedRun.setEmployeeCount(count);
        BonusRun finalRun = runs.save(savedRun);

        trace.put("cycleCode", cycle.getCode());
        trace.put("anchorDate", anchor.toString());
        trace.put("eligibleReviews", eligible.size());
        trace.put("itemsCreated", count);
        trace.put("total", total.toPlainString());
        trace.put("perEmployee", perEmployeeTrace);

        audit.record(MODULE, "BonusRun", finalRun.getId().toString(),
                "GENERATE", null,
                Map.of("runNo", finalRun.getRunNo(),
                        "trace", trace));
        return finalRun;
    }

    /**
     * Submit a GENERATED run for approval (M200 / PRD §8.13.12 AC).
     * Transitions the run to PENDING_APPROVAL and starts the
     * BONUS_RUN_APPROVAL workflow.
     */
    @Transactional
    public BonusRun submit(UUID runId) {
        BonusRun run = get(runId);
        if (run.getStatus() != BonusRunStatus.GENERATED) {
            throw new BadRequestException(
                    "Only GENERATED bonus runs can be submitted for approval (was " + run.getStatus() + ")");
        }
        BonusRunResponse before = BonusRunResponse.from(run);
        run.setStatus(BonusRunStatus.PENDING_APPROVAL);
        WorkflowInstance wf = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                WORKFLOW_ENTITY,
                run.getId().toString(),
                run.getRunNo() + " — " + run.getPeriodYear() + "/" + run.getPeriodMonth()
                        + " — total " + run.getTotalAmount() + " " + run.getCurrency()
                        + " (" + run.getEmployeeCount() + " employees)",
                Map.of(
                        "runNo", run.getRunNo(),
                        "periodYear", run.getPeriodYear(),
                        "periodMonth", run.getPeriodMonth(),
                        "totalAmount", run.getTotalAmount() == null ? "0" : run.getTotalAmount().toPlainString(),
                        "currency", run.getCurrency())));
        run.setWorkflowInstanceId(wf.getId());
        BonusRun saved = runs.save(run);
        audit.record(MODULE, WORKFLOW_ENTITY, runId.toString(),
                "SUBMIT_FOR_APPROVAL", before, BonusRunResponse.from(saved));
        return saved;
    }

    @Transactional
    public BonusRun onApproved(UUID runId, String comment) {
        BonusRun run = get(runId);
        if (run.getStatus() != BonusRunStatus.PENDING_APPROVAL) return run;
        run.setStatus(BonusRunStatus.APPROVED);
        BonusRun saved = runs.save(run);
        audit.record(MODULE, WORKFLOW_ENTITY, runId.toString(), "APPROVED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public BonusRun onRejected(UUID runId, String comment) {
        BonusRun run = get(runId);
        if (run.getStatus() != BonusRunStatus.PENDING_APPROVAL) return run;
        run.setStatus(BonusRunStatus.GENERATED);    // revert to editable
        run.setWorkflowInstanceId(null);
        BonusRun saved = runs.save(run);
        audit.record(MODULE, WORKFLOW_ENTITY, runId.toString(), "REJECTED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    /**
     * Push an APPROVED run's items into {@code payroll.payroll_bonus} against
     * a target payroll run (must exist + be in a writable state). Items with
     * zero amount are skipped silently. Skips items that have already been
     * pushed (idempotent if called twice).
     */
    @Transactional
    public BonusRun push(UUID runId, UUID targetPayrollRunId) {
        BonusRun run = get(runId);
        if (run.getStatus() != BonusRunStatus.APPROVED) {
            throw new BadRequestException(
                    "Only APPROVED bonus runs can be pushed (was " + run.getStatus() + ")");
        }
        PayrollRun payrollRun = payrollRuns.findById(targetPayrollRunId)
                .orElseThrow(() -> new BadRequestException(
                        "Target payroll run not found: " + targetPayrollRunId));
        if (payrollRun.getStatus() == PayrollRunStatus.PAID
                || payrollRun.getStatus() == PayrollRunStatus.CLOSED) {
            throw new BadRequestException(
                    "Cannot push to a PAID/CLOSED payroll run (was " + payrollRun.getStatus() + ")");
        }

        BonusRunResponse before = BonusRunResponse.from(run);
        int pushed = 0;
        for (BonusRunItem item : items.findByRunIdOrderByCreatedAt(runId)) {
            if (item.getPushedPayrollBonusId() != null) continue;
            if (item.getBonusAmount() == null || item.getBonusAmount().signum() == 0) continue;

            PayrollBonus pb = new PayrollBonus();
            pb.setRunId(payrollRun.getId());
            pb.setEmployeeId(item.getEmployeeId());
            pb.setBonusType(BonusType.PERFORMANCE);
            pb.setAmount(item.getBonusAmount());
            pb.setNote(String.format(
                    "Bonus run %s — %s%s",
                    run.getRunNo(),
                    item.getSource().name(),
                    item.getRecommendation() == null ? "" : " (" + item.getRecommendation() + ")"));
            pb.setCreatedBy(currentRequest.username());
            PayrollBonus savedBonus = payrollBonuses.save(pb);

            item.setPushedPayrollBonusId(savedBonus.getId());
            items.save(item);
            pushed++;
        }

        run.setTargetPayrollRunId(payrollRun.getId());
        run.setStatus(BonusRunStatus.PUSHED);
        run.setPushedAt(OffsetDateTime.now());
        run.setPushedBy(currentRequest.username());
        BonusRun saved = runs.save(run);
        audit.record(MODULE, "BonusRun", runId.toString(),
                "PUSH", before,
                Map.of("targetPayrollRunNo", payrollRun.getRunNo(),
                        "itemsPushed", String.valueOf(pushed)));
        log.info("Bonus run {} pushed {} items into payroll run {}",
                run.getRunNo(), pushed, payrollRun.getRunNo());
        return saved;
    }

    @Transactional
    public BonusRun cancel(UUID runId, String reason) {
        BonusRun run = get(runId);
        if (run.getStatus() == BonusRunStatus.PUSHED || run.getStatus() == BonusRunStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Cannot cancel a " + run.getStatus() + " run");
        }
        BonusRunResponse before = BonusRunResponse.from(run);
        run.setStatus(BonusRunStatus.CANCELLED);
        BonusRun saved = runs.save(run);
        audit.record(MODULE, "BonusRun", runId.toString(),
                "CANCEL", before,
                Map.of("reason", reason == null ? "" : reason));
        return saved;
    }
}
