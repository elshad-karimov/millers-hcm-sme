package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.api.dto.ErpExportDtos.ErpExportResponse;
import az.millers.hcm.payroll.api.dto.ErpExportDtos.ErpGenerateRequest;
import az.millers.hcm.payroll.domain.ErpExport;
import az.millers.hcm.payroll.domain.ErpExportLine;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollRunStatus;
import az.millers.hcm.payroll.repo.ErpExportRepository;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.common.BusinessNumbers;

/**
 * Generates ERP payroll journal-entry export batches (M158 / §17.2).
 *
 * <p>Supported formats: CSV_GENERIC, CSV_1C, CSV_DYNAMICS365, JSON.
 *
 * <p>Journal structure (double-entry, in AZN):
 * <pre>
 *   DR  Payroll Expense (gross)    6000
 *   CR  Salaries Payable (net)     6000-001
 *   CR  Income Tax Payable         6000-002
 *   CR  DSMF Employee Payable      6000-003
 *   CR  DSMF Employer Payable      6000-004
 *   CR  MMI Employee Payable       6000-005
 *   CR  MMI Employer Payable       6000-006
 *   CR  Unemployment Employee Pay  6000-007
 *   CR  Unemployment Employer Pay  6000-008
 *   DR  Employer Contributions Exp 6001
 * </pre>
 */
@Service
public class ErpExportService {

    private static final Logger log = LoggerFactory.getLogger(ErpExportService.class);

    private static final String MODULE = "PAYROLL";
    private static final String ENTITY = "ErpExport";

    // ── Default GL account codes (configurable in a future enhancement) ────────
    private static final String ACC_GROSS_EXPENSE      = "6000";
    private static final String ACC_SALARY_PAYABLE     = "6000-001";
    private static final String ACC_INCOME_TAX         = "6000-002";
    private static final String ACC_DSMF_EMP           = "6000-003";
    private static final String ACC_DSMF_EMPLOYER      = "6000-004";
    private static final String ACC_MMI_EMP            = "6000-005";
    private static final String ACC_MMI_EMPLOYER       = "6000-006";
    private static final String ACC_UNEMPLOY_EMP       = "6000-007";
    private static final String ACC_UNEMPLOY_EMPLOYER  = "6000-008";
    private static final String ACC_EMPLOYER_EXP       = "6001";

    private final ErpExportRepository exports;
    private final PayrollRunRepository runs;
    private final PayrollResultRepository results;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ErpExportService(ErpExportRepository exports,
                            PayrollRunRepository runs,
                            PayrollResultRepository results,
                            AuditService audit,
                            CurrentRequest currentRequest) {
        this.exports        = exports;
        this.runs           = runs;
        this.results        = results;
        this.audit          = audit;
        this.currentRequest = currentRequest;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public List<ErpExportResponse> listByRun(UUID runId) {
        return exports.findByRunIdOrderByCreatedAtDesc(runId).stream()
                .map(e -> ErpExportResponse.from(e, false))
                .toList();
    }

    public List<ErpExportResponse> listAll() {
        return exports.findAllByOrderByCreatedAtDesc().stream()
                .map(e -> ErpExportResponse.from(e, false))
                .toList();
    }

    public ErpExportResponse get(UUID id) {
        return ErpExportResponse.from(require(id), true);
    }

    // ── Generate ─────────────────────────────────────────────────────────────

    @Transactional
    public ErpExportResponse generate(UUID runId, ErpGenerateRequest req) {
        PayrollRun run = runs.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollRun not found: " + runId));

        if (run.getStatus() != PayrollRunStatus.APPROVED && run.getStatus() != PayrollRunStatus.CLOSED
                && run.getStatus() != PayrollRunStatus.PAID) {
            throw new BadRequestException(
                    "ERP export requires run status APPROVED, PAID, or CLOSED; current: " + run.getStatus());
        }

        validateFormat(req.format());

        ErpExport export = new ErpExport();
        export.setExportNo(nextExportNo());
        export.setRunId(runId);
        export.setFormat(req.format());
        export.setStatus("GENERATING");
        export.setPostingDate(req.postingDate());
        export.setReferenceNo(req.referenceNo());
        export.setJournalType(req.journalType() != null ? req.journalType() : "Payroll");
        export.setCreatedBy(currentRequest.username());
        export = exports.save(export);

        try {
            buildJournal(export, run);
            export.setStatus("READY");
            export.setGeneratedAt(OffsetDateTime.now());
        } catch (Exception ex) {
            export.setStatus("FAILED");
            export.setErrorMessage(ex.getMessage());
            log.error("ERP export generation failed for run {}", runId, ex);
        }
        export = exports.save(export);

        audit.record(MODULE, ENTITY, export.getId().toString(), "GENERATED",
                null, Map.of("exportNo", export.getExportNo(), "runId", runId,
                             "format", req.format(), "status", export.getStatus()));
        return ErpExportResponse.from(export, true);
    }

    // ── Download ─────────────────────────────────────────────────────────────

    public byte[] download(UUID id) {
        ErpExport export = require(id);
        if (!"READY".equals(export.getStatus())) {
            throw new BadRequestException("Export is not ready: " + export.getStatus());
        }
        return renderFile(export);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private void buildJournal(ErpExport export, PayrollRun run) {
        List<PayrollResult> payrollResults = results.findByRunIdOrderByEmployeeIdAsc(run.getId());
        int empCount = payrollResults.size();

        // Aggregate totals
        BigDecimal totalGross       = BigDecimal.ZERO;
        BigDecimal totalNet         = BigDecimal.ZERO;
        BigDecimal totalIncomeTax   = BigDecimal.ZERO;
        BigDecimal totalDsmfEmp     = BigDecimal.ZERO;
        BigDecimal totalDsmfEr      = BigDecimal.ZERO;
        BigDecimal totalMmiEmp      = BigDecimal.ZERO;
        BigDecimal totalMmiEr       = BigDecimal.ZERO;
        BigDecimal totalUnemplEmp   = BigDecimal.ZERO;
        BigDecimal totalUnemplEr    = BigDecimal.ZERO;

        for (PayrollResult r : payrollResults) {
            totalGross     = totalGross.add(r.getGrossAmount());
            totalNet       = totalNet.add(r.getNetAmount());
            totalIncomeTax = totalIncomeTax.add(r.getIncomeTax());
            totalDsmfEmp   = totalDsmfEmp.add(r.getDsmfEmployee());
            totalDsmfEr    = totalDsmfEr.add(r.getDsmfEmployer());
            totalMmiEmp    = totalMmiEmp.add(r.getMmiEmployee());
            totalMmiEr     = totalMmiEr.add(r.getMmiEmployer());
            totalUnemplEmp = totalUnemplEmp.add(r.getUnemplEmployee());
            totalUnemplEr  = totalUnemplEr.add(r.getUnemplEmployer());
        }
        BigDecimal totalEmployerContrib = totalDsmfEr.add(totalMmiEr).add(totalUnemplEr);
        String period = run.getPeriodYear() + "-" + String.format("%02d", run.getPeriodMonth());

        // Build journal lines
        List<ErpExportLine> lineList = new ArrayList<>();
        int lineNo = 1;

        // DR Payroll Expense (gross salaries)
        lineList.add(line(export, lineNo++, ACC_GROSS_EXPENSE, "Payroll Expense",
                period, empCount, totalGross, BigDecimal.ZERO));
        // DR Employer Contributions Expense
        lineList.add(line(export, lineNo++, ACC_EMPLOYER_EXP, "Employer Contributions",
                period, empCount, totalEmployerContrib, BigDecimal.ZERO));
        // CR Salaries Payable (net)
        lineList.add(line(export, lineNo++, ACC_SALARY_PAYABLE, "Salaries Payable",
                period, empCount, BigDecimal.ZERO, totalNet));
        // CR Income Tax Payable
        lineList.add(line(export, lineNo++, ACC_INCOME_TAX, "Income Tax Payable",
                period, empCount, BigDecimal.ZERO, totalIncomeTax));
        // CR DSMF Employee
        lineList.add(line(export, lineNo++, ACC_DSMF_EMP, "DSMF Employee Payable",
                period, empCount, BigDecimal.ZERO, totalDsmfEmp));
        // CR DSMF Employer
        lineList.add(line(export, lineNo++, ACC_DSMF_EMPLOYER, "DSMF Employer Payable",
                period, empCount, BigDecimal.ZERO, totalDsmfEr));
        // CR MMI Employee
        lineList.add(line(export, lineNo++, ACC_MMI_EMP, "MMI Employee Payable",
                period, empCount, BigDecimal.ZERO, totalMmiEmp));
        // CR MMI Employer
        lineList.add(line(export, lineNo++, ACC_MMI_EMPLOYER, "MMI Employer Payable",
                period, empCount, BigDecimal.ZERO, totalMmiEr));
        // CR Unemployment Employee
        lineList.add(line(export, lineNo++, ACC_UNEMPLOY_EMP, "Unemployment Emp Payable",
                period, empCount, BigDecimal.ZERO, totalUnemplEmp));
        // CR Unemployment Employer
        lineList.add(line(export, lineNo, ACC_UNEMPLOY_EMPLOYER, "Unemployment Er Payable",
                period, empCount, BigDecimal.ZERO, totalUnemplEr));

        export.getLines().addAll(lineList);

        BigDecimal totalDebit = totalGross.add(totalEmployerContrib)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCredit = totalNet.add(totalIncomeTax)
                .add(totalDsmfEmp).add(totalDsmfEr)
                .add(totalMmiEmp).add(totalMmiEr)
                .add(totalUnemplEmp).add(totalUnemplEr)
                .setScale(2, RoundingMode.HALF_UP);

        export.setLineCount(lineList.size());
        export.setTotalDebit(totalDebit);
        export.setTotalCredit(totalCredit);
        export.setFileSizeBytes((long) renderFile(export).length);
    }

    private ErpExportLine line(ErpExport export, int lineNo, String accountCode,
                               String accountName, String period, int empCount,
                               BigDecimal debit, BigDecimal credit) {
        ErpExportLine l = new ErpExportLine();
        l.setExport(export);
        l.setLineNo(lineNo);
        l.setAccountCode(accountCode);
        l.setAccountName(accountName);
        l.setDescription("Payroll " + period);
        l.setEmployeeCount(empCount);
        l.setDebit(debit.setScale(2, RoundingMode.HALF_UP));
        l.setCredit(credit.setScale(2, RoundingMode.HALF_UP));
        return l;
    }

    private byte[] renderFile(ErpExport export) {
        return switch (export.getFormat()) {
            case "JSON"           -> renderJson(export);
            case "CSV_1C"         -> renderCsv1C(export);
            case "CSV_DYNAMICS365"-> renderCsvDynamics(export);
            default               -> renderCsvGeneric(export);
        };
    }

    private byte[] renderCsvGeneric(ErpExport export) {
        StringBuilder sb = new StringBuilder();
        sb.append("LineNo,AccountCode,AccountName,Description,Debit,Credit,Currency,EmployeeCount\n");
        for (ErpExportLine l : export.getLines()) {
            sb.append(l.getLineNo()).append(',')
              .append(q(l.getAccountCode())).append(',')
              .append(q(l.getAccountName())).append(',')
              .append(q(l.getDescription())).append(',')
              .append(l.getDebit().toPlainString()).append(',')
              .append(l.getCredit().toPlainString()).append(',')
              .append(l.getCurrency()).append(',')
              .append(l.getEmployeeCount()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] renderCsv1C(ErpExport export) {
        // 1C Accounting 8.x universal exchange format (simplified)
        StringBuilder sb = new StringBuilder();
        sb.append("\"ПолноеНаименование\",\"Счет\",\"Субконто1\",\"Сумма\",\"ВидДвижения\"\n");
        for (ErpExportLine l : export.getLines()) {
            if (l.getDebit().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(q(l.getAccountName())).append(',')
                  .append(q(l.getAccountCode())).append(",\"\",")
                  .append(l.getDebit().toPlainString()).append(",\"Дебет\"\n");
            }
            if (l.getCredit().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(q(l.getAccountName())).append(',')
                  .append(q(l.getAccountCode())).append(",\"\",")
                  .append(l.getCredit().toPlainString()).append(",\"Кредит\"\n");
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] renderCsvDynamics(ErpExport export) {
        // Dynamics 365 Finance journal import format
        StringBuilder sb = new StringBuilder();
        sb.append("\"JOURNALNUM\",\"TRANSDATE\",\"ACCOUNTNUM\",\"ACCOUNTNAME\",")
          .append("\"DEBIT\",\"CREDIT\",\"CURRENCY\",\"TXT\"\n");
        for (ErpExportLine l : export.getLines()) {
            sb.append(q(export.getExportNo())).append(',')
              .append(export.getPostingDate()).append(',')
              .append(q(l.getAccountCode())).append(',')
              .append(q(l.getAccountName())).append(',')
              .append(l.getDebit().toPlainString()).append(',')
              .append(l.getCredit().toPlainString()).append(',')
              .append(q(l.getCurrency())).append(',')
              .append(q(l.getDescription())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] renderJson(ErpExport export) {
        StringBuilder sb = new StringBuilder("{\"exportNo\":\"").append(export.getExportNo())
                .append("\",\"runId\":\"").append(export.getRunId())
                .append("\",\"postingDate\":\"").append(export.getPostingDate())
                .append("\",\"journalType\":\"").append(export.getJournalType())
                .append("\",\"totalDebit\":").append(export.getTotalDebit())
                .append(",\"totalCredit\":").append(export.getTotalCredit())
                .append(",\"lines\":[");
        boolean first = true;
        for (ErpExportLine l : export.getLines()) {
            if (!first) sb.append(',');
            sb.append("{\"lineNo\":").append(l.getLineNo())
              .append(",\"accountCode\":\"").append(l.getAccountCode()).append('"')
              .append(",\"accountName\":\"").append(l.getAccountName()).append('"')
              .append(",\"debit\":").append(l.getDebit())
              .append(",\"credit\":").append(l.getCredit())
              .append(",\"currency\":\"").append(l.getCurrency()).append('"')
              .append(",\"description\":\"").append(l.getDescription()).append('"')
              .append('}');
            first = false;
        }
        sb.append("]}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String q(String s) {
        if (s == null) return "\"\"";
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    private ErpExport require(UUID id) {
        return exports.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ErpExport not found: " + id));
    }

    private void validateFormat(String format) {
        if (!List.of("CSV_GENERIC", "CSV_1C", "CSV_DYNAMICS365", "JSON").contains(format)) {
            throw new BadRequestException("Invalid format: " + format);
        }
    }

    private String nextExportNo() {
        int next = exports.findMaxSeq() + 1;
        return BusinessNumbers.format("EXP", 5, next);
    }
}
