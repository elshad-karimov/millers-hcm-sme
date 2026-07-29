package az.millers.hcm.payroll.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.domain.CostCenterAllocation;
import az.millers.hcm.payroll.domain.GLAccountMapping;
import az.millers.hcm.payroll.domain.GLAccountType;
import az.millers.hcm.payroll.domain.GLJournal;
import az.millers.hcm.payroll.domain.GLJournalLine;
import az.millers.hcm.payroll.domain.GLJournalStatus;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.domain.PayrollResultCostSplit;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollRunStatus;
import az.millers.hcm.payroll.repo.GLAccountMappingRepository;
import az.millers.hcm.payroll.repo.GLJournalLineRepository;
import az.millers.hcm.payroll.repo.GLJournalRepository;
import az.millers.hcm.payroll.repo.PayrollResultCostSplitRepository;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M355 — GL Journal generation from payroll runs.
 */
@Service
public class GLJournalService {

    private static final Logger log = LoggerFactory.getLogger(GLJournalService.class);
    private static final String MODULE = "PAYROLL";
    private static final String ENTITY = "GlJournal";

    private final PayrollRunRepository runs;
    private final PayrollResultRepository results;
    private final PayrollResultCostSplitRepository costSplits;
    private final GLJournalRepository journals;
    private final GLJournalLineRepository journalLines;
    private final GLAccountMappingRepository mappings;
    private final CostCenterAllocationService allocationService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public GLJournalService(PayrollRunRepository runs,
                             PayrollResultRepository results,
                             PayrollResultCostSplitRepository costSplits,
                             GLJournalRepository journals,
                             GLJournalLineRepository journalLines,
                             GLAccountMappingRepository mappings,
                             CostCenterAllocationService allocationService,
                             AuditService audit,
                             CurrentRequest currentRequest) {
        this.runs = runs;
        this.results = results;
        this.costSplits = costSplits;
        this.journals = journals;
        this.journalLines = journalLines;
        this.mappings = mappings;
        this.allocationService = allocationService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    /**
     * Generate GL journal for a payroll run.
     * Run must be APPROVED or PAID. Replaces any existing journal.
     */
    @Transactional
    public GLJournal generate(UUID runId) {
        String tenantId = TenantContext.current();

        PayrollRun run = runs.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found: " + runId));

        if (run.getStatus() != PayrollRunStatus.APPROVED && run.getStatus() != PayrollRunStatus.PAID) {
            throw new BadRequestException("RUN_STATUS_INVALID");
        }

        // A DRAFT journal may be regenerated (idempotent replace), but APPROVED or POSTED
        // journals are locked accounting records and must never be silently overwritten.
        journals.findByRunId(runId).ifPresent(existing -> {
            if (existing.getStatus() == GLJournalStatus.APPROVED || existing.getStatus() == GLJournalStatus.POSTED) {
                throw new BadRequestException("JOURNAL_ALREADY_APPROVED_OR_POSTED");
            }
            journalLines.deleteByJournalId(existing.getId());
            journals.deleteByRunId(runId);
        });

        List<PayrollResult> runResults = results.findByRunIdOrderByEmployeeIdAsc(runId);
        LocalDate periodStart = LocalDate.of(run.getPeriodYear(), run.getPeriodMonth(), 1);

        // Step 1: Build cost splits for each result
        for (PayrollResult result : runResults) {
            // Delete prior splits for this result
            costSplits.deleteByResultId(result.getId());

            List<CostCenterAllocation> allocList = allocationService.resolveAllocations(
                    result.getEmployeeId(), periodStart);

            for (CostCenterAllocation alloc : allocList) {
                BigDecimal grossShare = result.getGrossAmount()
                        .multiply(alloc.getAllocationPct())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                BigDecimal netShare = result.getNetAmount()
                        .multiply(alloc.getAllocationPct())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                PayrollResultCostSplit split = new PayrollResultCostSplit();
                split.setTenantId(tenantId);
                split.setResultId(result.getId());
                split.setCostCenterCode(alloc.getCostCenterCode());
                split.setGrossAmount(grossShare);
                split.setNetAmount(netShare);
                split.setAllocationPct(alloc.getAllocationPct());
                costSplits.save(split);
            }
        }

        // Step 2: Build GL journal lines
        List<GLJournalLine> lines = new ArrayList<>();
        int sequence = 0;

        // DEBIT: Salary expense per cost center
        Map<String, BigDecimal> grossByCostCenter = new HashMap<>();
        for (PayrollResult result : runResults) {
            List<PayrollResultCostSplit> splits = costSplits.findByResultId(result.getId());
            for (PayrollResultCostSplit split : splits) {
                grossByCostCenter.merge(split.getCostCenterCode(), split.getGrossAmount(), BigDecimal::add);
            }
        }

        for (Map.Entry<String, BigDecimal> entry : grossByCostCenter.entrySet()) {
            String costCenter = entry.getKey();
            BigDecimal amount = entry.getValue();

            AccountInfo account = resolveAccount("EARNING", null, GLAccountType.DEBIT);

            GLJournalLine line = new GLJournalLine();
            line.setTenantId(tenantId);
            line.setSequenceNo(++sequence);
            line.setCostCenterCode(costCenter);
            line.setComponentKind("EARNING");
            line.setAccountType(GLAccountType.DEBIT);
            line.setGlAccountCode(account.code());
            line.setGlAccountName(account.name());
            line.setAmount(amount);
            line.setDescription("Salary expense - " + costCenter);
            lines.add(line);
        }

        // CREDIT: Net pay payable
        BigDecimal totalNet = runResults.stream()
                .map(PayrollResult::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalNet.compareTo(BigDecimal.ZERO) > 0) {
            AccountInfo netAccount = resolveAccount("NET_PAY", null, GLAccountType.CREDIT);
            GLJournalLine netLine = new GLJournalLine();
            netLine.setTenantId(tenantId);
            netLine.setSequenceNo(++sequence);
            netLine.setComponentKind("NET_PAY");
            netLine.setAccountType(GLAccountType.CREDIT);
            netLine.setGlAccountCode(netAccount.code());
            netLine.setGlAccountName(netAccount.name());
            netLine.setAmount(totalNet);
            netLine.setDescription("Net pay payable");
            lines.add(netLine);
        }

        // CREDIT: Income tax payable
        BigDecimal totalIncomeTax = runResults.stream()
                .map(PayrollResult::getIncomeTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalIncomeTax.compareTo(BigDecimal.ZERO) > 0) {
            AccountInfo taxAccount = resolveAccount("INCOME_TAX", null, GLAccountType.CREDIT);
            GLJournalLine taxLine = new GLJournalLine();
            taxLine.setTenantId(tenantId);
            taxLine.setSequenceNo(++sequence);
            taxLine.setComponentKind("INCOME_TAX");
            taxLine.setAccountType(GLAccountType.CREDIT);
            taxLine.setGlAccountCode(taxAccount.code());
            taxLine.setGlAccountName(taxAccount.name());
            taxLine.setAmount(totalIncomeTax);
            taxLine.setDescription("Income tax payable");
            lines.add(taxLine);
        }

        // CREDIT: DSMF employee payable
        BigDecimal totalDsmfEmp = runResults.stream()
                .map(PayrollResult::getDsmfEmployee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDsmfEmp.compareTo(BigDecimal.ZERO) > 0) {
            AccountInfo dsmfEmpAccount = resolveAccount("DSMF_EMPLOYEE", null, GLAccountType.CREDIT);
            GLJournalLine dsmfEmpLine = new GLJournalLine();
            dsmfEmpLine.setTenantId(tenantId);
            dsmfEmpLine.setSequenceNo(++sequence);
            dsmfEmpLine.setComponentKind("DSMF_EMPLOYEE");
            dsmfEmpLine.setAccountType(GLAccountType.CREDIT);
            dsmfEmpLine.setGlAccountCode(dsmfEmpAccount.code());
            dsmfEmpLine.setGlAccountName(dsmfEmpAccount.name());
            dsmfEmpLine.setAmount(totalDsmfEmp);
            dsmfEmpLine.setDescription("DSMF employee payable");
            lines.add(dsmfEmpLine);
        }

        // CREDIT: DSMF employer payable
        BigDecimal totalDsmfEr = runResults.stream()
                .map(PayrollResult::getDsmfEmployer)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDsmfEr.compareTo(BigDecimal.ZERO) > 0) {
            AccountInfo dsmfErAccount = resolveAccount("DSMF_EMPLOYER", null, GLAccountType.CREDIT);
            GLJournalLine dsmfErLine = new GLJournalLine();
            dsmfErLine.setTenantId(tenantId);
            dsmfErLine.setSequenceNo(++sequence);
            dsmfErLine.setComponentKind("DSMF_EMPLOYER");
            dsmfErLine.setAccountType(GLAccountType.CREDIT);
            dsmfErLine.setGlAccountCode(dsmfErAccount.code());
            dsmfErLine.setGlAccountName(dsmfErAccount.name());
            dsmfErLine.setAmount(totalDsmfEr);
            dsmfErLine.setDescription("DSMF employer payable");
            lines.add(dsmfErLine);
        }

        // CREDIT: MMI employee payable
        BigDecimal totalMmiEmp = runResults.stream()
                .map(PayrollResult::getMmiEmployee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalMmiEmp.compareTo(BigDecimal.ZERO) > 0) {
            AccountInfo mmiEmpAccount = resolveAccount("MMI_EMPLOYEE", null, GLAccountType.CREDIT);
            GLJournalLine mmiEmpLine = new GLJournalLine();
            mmiEmpLine.setTenantId(tenantId);
            mmiEmpLine.setSequenceNo(++sequence);
            mmiEmpLine.setComponentKind("MMI_EMPLOYEE");
            mmiEmpLine.setAccountType(GLAccountType.CREDIT);
            mmiEmpLine.setGlAccountCode(mmiEmpAccount.code());
            mmiEmpLine.setGlAccountName(mmiEmpAccount.name());
            mmiEmpLine.setAmount(totalMmiEmp);
            mmiEmpLine.setDescription("MMI employee payable");
            lines.add(mmiEmpLine);
        }

        // CREDIT: MMI employer payable
        BigDecimal totalMmiEr = runResults.stream()
                .map(PayrollResult::getMmiEmployer)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalMmiEr.compareTo(BigDecimal.ZERO) > 0) {
            AccountInfo mmiErAccount = resolveAccount("MMI_EMPLOYER", null, GLAccountType.CREDIT);
            GLJournalLine mmiErLine = new GLJournalLine();
            mmiErLine.setTenantId(tenantId);
            mmiErLine.setSequenceNo(++sequence);
            mmiErLine.setComponentKind("MMI_EMPLOYER");
            mmiErLine.setAccountType(GLAccountType.CREDIT);
            mmiErLine.setGlAccountCode(mmiErAccount.code());
            mmiErLine.setGlAccountName(mmiErAccount.name());
            mmiErLine.setAmount(totalMmiEr);
            mmiErLine.setDescription("MMI employer payable");
            lines.add(mmiErLine);
        }

        // CREDIT: Unemployment employee payable
        BigDecimal totalUnemplEmp = runResults.stream()
                .map(PayrollResult::getUnemplEmployee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalUnemplEmp.compareTo(BigDecimal.ZERO) > 0) {
            AccountInfo unemplEmpAccount = resolveAccount("UNEMPLOYMENT_EMPLOYEE", null, GLAccountType.CREDIT);
            GLJournalLine unemplEmpLine = new GLJournalLine();
            unemplEmpLine.setTenantId(tenantId);
            unemplEmpLine.setSequenceNo(++sequence);
            unemplEmpLine.setComponentKind("UNEMPLOYMENT_EMPLOYEE");
            unemplEmpLine.setAccountType(GLAccountType.CREDIT);
            unemplEmpLine.setGlAccountCode(unemplEmpAccount.code());
            unemplEmpLine.setGlAccountName(unemplEmpAccount.name());
            unemplEmpLine.setAmount(totalUnemplEmp);
            unemplEmpLine.setDescription("Unemployment employee payable");
            lines.add(unemplEmpLine);
        }

        // CREDIT: Unemployment employer payable
        BigDecimal totalUnemplEr = runResults.stream()
                .map(PayrollResult::getUnemplEmployer)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalUnemplEr.compareTo(BigDecimal.ZERO) > 0) {
            AccountInfo unemplErAccount = resolveAccount("UNEMPLOYMENT_EMPLOYER", null, GLAccountType.CREDIT);
            GLJournalLine unemplErLine = new GLJournalLine();
            unemplErLine.setTenantId(tenantId);
            unemplErLine.setSequenceNo(++sequence);
            unemplErLine.setComponentKind("UNEMPLOYMENT_EMPLOYER");
            unemplErLine.setAccountType(GLAccountType.CREDIT);
            unemplErLine.setGlAccountCode(unemplErAccount.code());
            unemplErLine.setGlAccountName(unemplErAccount.name());
            unemplErLine.setAmount(totalUnemplEr);
            unemplErLine.setDescription("Unemployment employer payable");
            lines.add(unemplErLine);
        }

        // CREDIT: Other deductions (deductionAmount from results)
        BigDecimal totalDeductions = runResults.stream()
                .map(PayrollResult::getDeductionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDeductions.compareTo(BigDecimal.ZERO) > 0) {
            AccountInfo deductAccount = resolveAccount("DEDUCTION", null, GLAccountType.CREDIT);
            GLJournalLine deductLine = new GLJournalLine();
            deductLine.setTenantId(tenantId);
            deductLine.setSequenceNo(++sequence);
            deductLine.setComponentKind("DEDUCTION");
            deductLine.setAccountType(GLAccountType.CREDIT);
            deductLine.setGlAccountCode(deductAccount.code());
            deductLine.setGlAccountName(deductAccount.name());
            deductLine.setAmount(totalDeductions);
            deductLine.setDescription("Other deductions payable");
            lines.add(deductLine);
        }

        // Calculate totals
        BigDecimal totalDebit = lines.stream()
                .filter(l -> l.getAccountType() == GLAccountType.DEBIT)
                .map(GLJournalLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = lines.stream()
                .filter(l -> l.getAccountType() == GLAccountType.CREDIT)
                .map(GLJournalLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Verify balance
        if (totalDebit.compareTo(totalCredit) != 0) {
            log.error("Journal not balanced for run {}: debit={}, credit={}", runId, totalDebit, totalCredit);
            throw new BadRequestException("JOURNAL_NOT_BALANCED");
        }

        // Create journal header
        GLJournal journal = new GLJournal();
        journal.setTenantId(tenantId);
        journal.setRunId(runId);
        journal.setJournalDate(periodStart);
        journal.setStatus(GLJournalStatus.DRAFT);
        journal.setTotalDebit(totalDebit);
        journal.setTotalCredit(totalCredit);
        journal.setCreatedBy(currentRequest.username());
        journals.save(journal);

        // Save lines
        for (GLJournalLine line : lines) {
            line.setJournalId(journal.getId());
            journalLines.save(line);
        }

        audit.record(MODULE, ENTITY, runId.toString(), "GENERATED", null,
                "Lines: " + lines.size() + ", Debit: " + totalDebit + ", Credit: " + totalCredit);

        return journal;
    }

    @Transactional(readOnly = true)
    public JournalResponse get(UUID runId) {
        GLJournal journal = journals.findByRunId(runId)
                .orElseThrow(() -> new ResourceNotFoundException("GL journal not found for run: " + runId));

        List<GLJournalLine> lines = journalLines.findByJournalIdOrderBySequenceNo(journal.getId());

        return new JournalResponse(journal, lines);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(UUID runId) {
        JournalResponse resp = get(runId);

        StringBuilder csv = new StringBuilder();
        csv.append("Sequence,CostCenter,ComponentKind,AccountType,GLCode,GLName,Amount,Description\n");

        for (GLJournalLine line : resp.lines()) {
            csv.append(line.getSequenceNo()).append(",");
            csv.append(line.getCostCenterCode() != null ? line.getCostCenterCode() : "").append(",");
            csv.append(line.getComponentKind() != null ? line.getComponentKind() : "").append(",");
            csv.append(line.getAccountType()).append(",");
            csv.append(line.getGlAccountCode()).append(",");
            csv.append("\"").append(line.getGlAccountName()).append("\",");
            csv.append(line.getAmount()).append(",");
            csv.append(line.getDescription() != null ? "\"" + line.getDescription() + "\"" : "").append("\n");
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * M465 — Approve a DRAFT journal. Only DRAFT journals may be approved.
     */
    @Transactional
    public GLJournal approve(UUID runId) {
        GLJournal journal = journals.findByRunId(runId)
                .orElseThrow(() -> new ResourceNotFoundException("GL journal not found for run: " + runId));

        if (journal.getStatus() != GLJournalStatus.DRAFT) {
            throw new BadRequestException("JOURNAL_NOT_DRAFT");
        }

        journal.setStatus(GLJournalStatus.APPROVED);
        journal.setApprovedBy(currentRequest.username());
        journal.setApprovedAt(OffsetDateTime.now());
        journals.save(journal);

        audit.record(MODULE, ENTITY, runId.toString(), "APPROVED", null, "Status: APPROVED");

        return journal;
    }

    /**
     * M465 — Post an APPROVED journal to the GL. Only APPROVED journals may be posted.
     */
    @Transactional
    public GLJournal post(UUID runId) {
        GLJournal journal = journals.findByRunId(runId)
                .orElseThrow(() -> new ResourceNotFoundException("GL journal not found for run: " + runId));

        if (journal.getStatus() != GLJournalStatus.APPROVED) {
            throw new BadRequestException("JOURNAL_NOT_APPROVED");
        }

        journal.setStatus(GLJournalStatus.POSTED);
        journal.setPostedBy(currentRequest.username());
        journal.setPostedAt(OffsetDateTime.now());
        journals.save(journal);

        audit.record(MODULE, ENTITY, runId.toString(), "POSTED", null,
                "Posted by: " + currentRequest.username() + ", Total: " + journal.getTotalDebit());

        return journal;
    }

    /**
     * M466 — Reverse a POSTED journal. Creates a new journal with inverted debit/credit lines,
     * marks the original as REVERSED, and links them. A journal may only be reversed once.
     */
    @Transactional
    public GLJournal reverse(UUID journalId) {
        String tenantId = TenantContext.current();

        GLJournal original = journals.findById(journalId)
                .orElseThrow(() -> new ResourceNotFoundException("GL journal not found: " + journalId));

        if (!tenantId.equals(original.getTenantId())) {
            throw new ResourceNotFoundException("GL journal not found: " + journalId);
        }

        if (original.getStatus() != GLJournalStatus.POSTED) {
            throw new BadRequestException("JOURNAL_NOT_POSTED");
        }

        if (original.getReversedJournalId() != null) {
            throw new BadRequestException("JOURNAL_ALREADY_REVERSED");
        }

        // Load original lines
        List<GLJournalLine> originalLines = journalLines.findByJournalIdOrderBySequenceNo(original.getId());

        // Create reversal journal header
        GLJournal reversal = new GLJournal();
        reversal.setTenantId(tenantId);
        reversal.setRunId(original.getRunId());
        reversal.setJournalDate(LocalDate.now());
        reversal.setStatus(GLJournalStatus.POSTED);
        reversal.setTotalDebit(original.getTotalDebit());
        reversal.setTotalCredit(original.getTotalCredit());
        reversal.setCreatedBy(currentRequest.username());
        reversal.setPostedBy(currentRequest.username());
        reversal.setPostedAt(OffsetDateTime.now());
        journals.save(reversal);

        // Audit the reversal journal creation
        audit.record(MODULE, ENTITY, reversal.getId().toString(), "CREATED_AS_REVERSAL", null,
                "Original: " + journalId);

        // Create inverted lines (swap debit ↔ credit)
        int sequence = 0;
        for (GLJournalLine origLine : originalLines) {
            GLJournalLine inverted = new GLJournalLine();
            inverted.setTenantId(tenantId);
            inverted.setJournalId(reversal.getId());
            inverted.setSequenceNo(++sequence);
            inverted.setCostCenterCode(origLine.getCostCenterCode());
            inverted.setComponentKind(origLine.getComponentKind());
            // Swap account type
            inverted.setAccountType(origLine.getAccountType() == GLAccountType.DEBIT
                    ? GLAccountType.CREDIT : GLAccountType.DEBIT);
            inverted.setGlAccountCode(origLine.getGlAccountCode());
            inverted.setGlAccountName(origLine.getGlAccountName());
            inverted.setAmount(origLine.getAmount());
            inverted.setDescription("REVERSAL: " + (origLine.getDescription() != null ? origLine.getDescription() : ""));
            journalLines.save(inverted);
        }

        // Mark original as reversed and link to reversal
        original.setStatus(GLJournalStatus.REVERSED);
        original.setReversedJournalId(reversal.getId());
        journals.save(original);

        audit.record(MODULE, ENTITY, journalId.toString(), "REVERSED", null,
                "Reversal journal: " + reversal.getId());

        return reversal;
    }

    /**
     * Resolve GL account, with fallback to default "Unmapped" if mapping missing.
     */
    private AccountInfo resolveAccount(String componentKind, String componentCode, GLAccountType accountType) {
        String tenantId = TenantContext.current();

        // Try exact match
        if (componentCode != null) {
            Optional<GLAccountMapping> exact = mappings.findExactMatch(
                    tenantId, componentKind, componentCode, accountType);
            if (exact.isPresent()) {
                GLAccountMapping m = exact.get();
                return new AccountInfo(m.getGlAccountCode(), m.getGlAccountName());
            }
        }

        // Try kind fallback
        Optional<GLAccountMapping> fallback = mappings.findKindFallback(
                tenantId, componentKind, accountType);
        if (fallback.isPresent()) {
            GLAccountMapping m = fallback.get();
            return new AccountInfo(m.getGlAccountCode(), m.getGlAccountName());
        }

        // No mapping — use fallback account
        log.warn("GL_MAPPING_MISSING for kind={}, code={}, type={}",
                componentKind, componentCode, accountType);
        return new AccountInfo("9999", "Unmapped");
    }

    private record AccountInfo(String code, String name) {}

    public record JournalResponse(GLJournal journal, List<GLJournalLine> lines) {}
}
