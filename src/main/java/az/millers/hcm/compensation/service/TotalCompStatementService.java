package az.millers.hcm.compensation.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import az.millers.hcm.attachment.domain.Attachment;
import az.millers.hcm.attachment.service.AttachmentService;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.domain.AllowanceStatus;
import az.millers.hcm.compbenefits.domain.EmployeeAllowance;
import az.millers.hcm.compbenefits.repo.EmployeeAllowanceRepository;
import az.millers.hcm.compensation.domain.CommissionPayout;
import az.millers.hcm.compensation.domain.IncentivePayout;
import az.millers.hcm.compensation.domain.TotalCompStatement;
import az.millers.hcm.compensation.repo.CommissionPayoutRepository;
import az.millers.hcm.compensation.repo.IncentivePayoutRepository;
import az.millers.hcm.compensation.repo.TotalCompStatementRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.AnnualPayrollSummary;
import az.millers.hcm.payroll.domain.BonusType;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.domain.PayrollBonus;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.repo.AnnualPayrollSummaryRepository;
import az.millers.hcm.payroll.repo.PayrollBonusRepository;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.service.CompensationService;
import az.millers.hcm.security.CurrentRequest;

/**
 * M368 — Total Compensation Statement service.
 * Assembles base salary, allowances, bonuses, incentives, commissions,
 * employer benefits, and employer contributions into a comprehensive statement.
 */
@Service
public class TotalCompStatementService {

    private static final Logger log = LoggerFactory.getLogger(TotalCompStatementService.class);
    private static final String MODULE = "compensation";
    private static final String ENTITY = "TotalCompStatement";
    private static final String TENANT = "default";

    private final TotalCompStatementRepository statements;
    private final EmployeeRepository employees;
    private final CompensationService compensationService;
    private final EmployeeAllowanceRepository allowances;
    private final PayrollBonusRepository bonuses;
    private final IncentivePayoutRepository incentivePayouts;
    private final CommissionPayoutRepository commissionPayouts;
    private final AnnualPayrollSummaryRepository annualSummaries;
    private final PayrollResultRepository payrollResults;
    private final AttachmentService attachmentService;
    private final az.millers.hcm.compbenefits.service.BenefitCostService benefitCost;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public TotalCompStatementService(TotalCompStatementRepository statements,
                                      EmployeeRepository employees,
                                      CompensationService compensationService,
                                      EmployeeAllowanceRepository allowances,
                                      PayrollBonusRepository bonuses,
                                      IncentivePayoutRepository incentivePayouts,
                                      CommissionPayoutRepository commissionPayouts,
                                      AnnualPayrollSummaryRepository annualSummaries,
                                      PayrollResultRepository payrollResults,
                                      AttachmentService attachmentService,
                                      az.millers.hcm.compbenefits.service.BenefitCostService benefitCost,
                                      AuditService audit,
                                      CurrentRequest currentRequest) {
        this.statements = statements;
        this.employees = employees;
        this.compensationService = compensationService;
        this.allowances = allowances;
        this.bonuses = bonuses;
        this.incentivePayouts = incentivePayouts;
        this.commissionPayouts = commissionPayouts;
        this.annualSummaries = annualSummaries;
        this.payrollResults = payrollResults;
        this.attachmentService = attachmentService;
        this.benefitCost = benefitCost;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public TotalCompStatement generate(UUID employeeId, int year) {
        Employee emp = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        // Assemble components
        BigDecimal baseSalary = calculateBaseSalary(employeeId, year);
        BigDecimal allowancesTotal = calculateAllowances(employeeId, year);
        BigDecimal bonusTotal = calculateBonuses(employeeId, year);
        BigDecimal incentivesTotal = calculateIncentives(employeeId, year);
        BigDecimal commissionTotal = calculateCommissions(employeeId, year);
        BigDecimal employerContributionsTotal = calculateEmployerContributions(employeeId, year);
        // HCM_11 M383 — employer benefit cost (was a ZERO placeholder): annual employer
        // contribution across the employee's benefit enrollments overlapping the year.
        BigDecimal employerBenefitsTotal = benefitCost.annualEmployerCost(employeeId, year);

        BigDecimal totalComp = baseSalary
                .add(allowancesTotal)
                .add(bonusTotal)
                .add(incentivesTotal)
                .add(commissionTotal)
                .add(employerBenefitsTotal)
                .add(employerContributionsTotal);

        // Upsert statement
        TotalCompStatement stmt = statements.findByTenantIdAndEmployeeIdAndYear(TENANT, employeeId, year)
                .orElse(new TotalCompStatement());

        stmt.setTenantId(TENANT);
        stmt.setEmployeeId(employeeId);
        stmt.setYear(year);
        stmt.setBaseSalary(baseSalary);
        stmt.setAllowancesTotal(allowancesTotal);
        stmt.setBonusTotal(bonusTotal);
        stmt.setIncentivesTotal(incentivesTotal);
        stmt.setCommissionTotal(commissionTotal);
        stmt.setEmployerBenefitsTotal(employerBenefitsTotal);
        stmt.setEmployerContributionsTotal(employerContributionsTotal);
        stmt.setTotalComp(totalComp);
        stmt.setStatus("GENERATED");

        TotalCompStatement saved = statements.save(stmt);

        // Generate PDF
        byte[] pdfBytes = generatePdf(emp, saved);
        String filename = String.format("TotalComp_%s_%d.pdf", emp.getEmployeeNo(), year);

        // Delete existing attachment if any (idempotent)
        List<Attachment> existing = attachmentService.list(MODULE, "totalcompstatement", saved.getId());
        existing.forEach(att -> attachmentService.delete(att.getId()));

        Attachment attachment = attachmentService.uploadBytes(
                MODULE,
                "totalcompstatement",
                saved.getId(),
                pdfBytes,
                filename,
                "application/pdf"
        );

        saved.setStoragePath(attachment.getObjectKey());
        saved = statements.save(saved);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "GENERATED", null,
                Map.of("employeeId", employeeId.toString(), "year", year, "totalComp", totalComp.toPlainString()));

        return saved;
    }

    @Transactional
    public Map<String, Object> generateAll(int year) {
        List<Employee> activeEmployees = employees.findAll().stream()
                .filter(e -> e.getHireDate() != null && e.getHireDate().getYear() <= year)
                .toList();

        int count = 0;
        for (Employee emp : activeEmployees) {
            try {
                generate(emp.getId(), year);
                count++;
            } catch (Exception e) {
                log.warn("Failed to generate statement for employee {}: {}", emp.getEmployeeNo(), e.getMessage());
            }
        }

        return Map.of("generated", count, "total", activeEmployees.size());
    }

    @Transactional
    public TotalCompStatement release(UUID id) {
        TotalCompStatement stmt = statements.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found: " + id));

        if (!"GENERATED".equals(stmt.getStatus())) {
            throw new BadRequestException("Only GENERATED statements can be released");
        }

        stmt.setStatus("RELEASED");
        stmt.setReleasedAt(OffsetDateTime.now());
        TotalCompStatement saved = statements.save(stmt);

        audit.record(MODULE, ENTITY, id.toString(), "RELEASED", null, null);
        return saved;
    }

    @Transactional
    public Map<String, Object> releaseAll(int year) {
        List<TotalCompStatement> stmts = statements.findByTenantIdAndYearOrderByEmployeeIdAsc(TENANT, year);
        int count = 0;
        for (TotalCompStatement stmt : stmts) {
            if ("GENERATED".equals(stmt.getStatus())) {
                release(stmt.getId());
                count++;
            }
        }
        return Map.of("released", count, "total", stmts.size());
    }

    @Transactional(readOnly = true)
    public List<TotalCompStatement> list(int year) {
        return statements.findByTenantIdAndYearOrderByEmployeeIdAsc(TENANT, year);
    }

    @Transactional(readOnly = true)
    public byte[] download(UUID id) {
        TotalCompStatement stmt = statements.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found: " + id));

        List<Attachment> attachments = attachmentService.list(MODULE, "totalcompstatement", stmt.getId());
        if (attachments.isEmpty()) {
            throw new ResourceNotFoundException("Statement PDF not yet generated");
        }

        try (var stream = attachmentService.download(attachments.get(0).getId())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download statement: " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Component Calculation
    // ══════════════════════════════════════════════════════════════════════════════

    private BigDecimal calculateBaseSalary(UUID employeeId, int year) {
        // Try annual summary first
        AnnualPayrollSummary summary = annualSummaries.findByEmployeeIdAndYear(employeeId, year).orElse(null);
        if (summary != null) {
            // Approximate annual base = (total gross - bonuses) assuming 12 months
            return summary.getTotalGross().subtract(summary.getTotalBonuses());
        }

        // Fallback: current salary annualized
        try {
            EmployeeCompensation comp = compensationService.getActiveOn(employeeId, LocalDate.now());
            return comp.getMonthlyBaseSalary().multiply(BigDecimal.valueOf(12));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal calculateAllowances(UUID employeeId, int year) {
        // Active allowances as of end of year, annualized
        LocalDate endOfYear = LocalDate.of(year, 12, 31);
        List<EmployeeAllowance> active = allowances.findActiveForEmployeeOn(employeeId, AllowanceStatus.ACTIVE, endOfYear);
        return active.stream()
                .map(a -> a.getAmount().multiply(BigDecimal.valueOf(12)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateBonuses(UUID employeeId, int year) {
        // Sum PERFORMANCE and ONE_TIME bonuses from payroll_bonus for the year
        // We need to query by run's period year - approximate via PayrollResult period_start
        List<PayrollResult> results = payrollResults.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
        BigDecimal total = BigDecimal.ZERO;
        for (PayrollResult result : results) {
            if (result.getPeriodStart().getYear() == year) {
                total = total.add(result.getBonusAmount());
            }
        }
        return total;
    }

    private BigDecimal calculateIncentives(UUID employeeId, int year) {
        List<IncentivePayout> payouts = incentivePayouts.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TENANT, employeeId);
        return payouts.stream()
                .filter(p -> "APPROVED".equals(p.getStatus()) || "PAID".equals(p.getStatus()))
                .filter(p -> p.getPeriod() != null && p.getPeriod().contains(String.valueOf(year)))
                .map(IncentivePayout::getPayoutAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateCommissions(UUID employeeId, int year) {
        List<CommissionPayout> payouts = commissionPayouts.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TENANT, employeeId);
        return payouts.stream()
                .filter(p -> "APPROVED".equals(p.getStatus()) || "PAID".equals(p.getStatus()))
                .filter(p -> p.getPeriod() != null && p.getPeriod().contains(String.valueOf(year)))
                .map(CommissionPayout::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateEmployerContributions(UUID employeeId, int year) {
        // Source from annual_payroll_summary (employer DSMF + MMI + unemployment)
        AnnualPayrollSummary summary = annualSummaries.findByEmployeeIdAndYear(employeeId, year).orElse(null);
        if (summary != null) {
            // Note: annual_payroll_summary doesn't have separate employer columns in the read schema
            // We'll sum from individual payroll_result rows for the year
            List<PayrollResult> results = payrollResults.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
            BigDecimal total = BigDecimal.ZERO;
            for (PayrollResult result : results) {
                if (result.getPeriodStart().getYear() == year) {
                    total = total
                            .add(result.getDsmfEmployer())
                            .add(result.getMmiEmployer())
                            .add(result.getUnemplEmployer());
                }
            }
            return total;
        }
        return BigDecimal.ZERO;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // PDF Generation (reuse PayslipPdfService pattern)
    // ══════════════════════════════════════════════════════════════════════════════

    private byte[] generatePdf(Employee emp, TotalCompStatement stmt) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);

        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // Header
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("TOTAL COMPENSATION STATEMENT", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            doc.add(title);

            Font yearFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            Paragraph yearPara = new Paragraph("Year: " + stmt.getYear(), yearFont);
            yearPara.setAlignment(Element.ALIGN_CENTER);
            yearPara.setSpacingAfter(20);
            doc.add(yearPara);

            // Employee details
            PdfPTable detailsTable = new PdfPTable(2);
            detailsTable.setWidthPercentage(90);
            detailsTable.setSpacingAfter(20);
            detailsTable.setWidths(new float[]{2, 3});

            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            addDetailRow(detailsTable, "Employee No:", emp.getEmployeeNo(), labelFont, valueFont);
            addDetailRow(detailsTable, "Name:", emp.getFirstName() + " " + emp.getLastName(), labelFont, valueFont);
            addDetailRow(detailsTable, "Position:", emp.getPositionTitle() != null ? emp.getPositionTitle() : "N/A", labelFont, valueFont);

            doc.add(detailsTable);

            // Compensation breakdown
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Paragraph compTitle = new Paragraph("Compensation Breakdown", sectionFont);
            compTitle.setSpacingBefore(10);
            compTitle.setSpacingAfter(5);
            doc.add(compTitle);

            PdfPTable compTable = new PdfPTable(2);
            compTable.setWidthPercentage(90);
            compTable.setWidths(new float[]{3, 2});

            addAmountRow(compTable, "Base Salary", stmt.getBaseSalary(), valueFont);
            addAmountRow(compTable, "Allowances", stmt.getAllowancesTotal(), valueFont);
            addAmountRow(compTable, "Bonuses", stmt.getBonusTotal(), valueFont);
            addAmountRow(compTable, "Incentives", stmt.getIncentivesTotal(), valueFont);
            addAmountRow(compTable, "Commissions", stmt.getCommissionTotal(), valueFont);
            addAmountRow(compTable, "Employer Benefits", stmt.getEmployerBenefitsTotal(), valueFont);
            addAmountRow(compTable, "Employer Contributions (DSMF/MMI/Unemployment)", stmt.getEmployerContributionsTotal(), valueFont);

            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            addAmountRow(compTable, "TOTAL COMPENSATION", stmt.getTotalComp(), totalFont);

            doc.add(compTable);

            // Footer
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9);
            Paragraph footer = new Paragraph(
                    "Generated on " + stmt.getGeneratedAt().toLocalDate() +
                    "\nThis statement is for informational purposes only.",
                    footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(20);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate total comp PDF: " + e.getMessage(), e);
        }
    }

    private void addDetailRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(PdfPCell.NO_BORDER);
        labelCell.setPadding(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(PdfPCell.NO_BORDER);
        valueCell.setPadding(5);
        table.addCell(valueCell);
    }

    private void addAmountRow(PdfPTable table, String label, BigDecimal amount, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setPadding(5);
        labelCell.setBackgroundColor(new java.awt.Color(248, 248, 252));
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(formatMoney(amount), font));
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private String formatMoney(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toString() + " AZN";
    }
}
