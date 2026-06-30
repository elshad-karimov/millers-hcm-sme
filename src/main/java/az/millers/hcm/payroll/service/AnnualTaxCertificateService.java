package az.millers.hcm.payroll.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Chunk;
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
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.AnnualPayrollSummary;
import az.millers.hcm.payroll.domain.AnnualTaxCertificate;
import az.millers.hcm.payroll.domain.CertificateStatus;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollRunStatus;
import az.millers.hcm.payroll.domain.RunType;
import az.millers.hcm.payroll.repo.AnnualPayrollSummaryRepository;
import az.millers.hcm.payroll.repo.AnnualTaxCertificateRepository;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class AnnualTaxCertificateService {

    private static final Logger log = LoggerFactory.getLogger(AnnualTaxCertificateService.class);
    private static final String EMPLOYER_VOEN = "1234567890"; // Placeholder
    private static final BigDecimal MONTHLY_EXEMPTION = new BigDecimal("200.00");

    private final AnnualPayrollSummaryRepository summaryRepo;
    private final AnnualTaxCertificateRepository certificateRepo;
    private final PayrollRunRepository runRepo;
    private final PayrollResultRepository resultRepo;
    private final EmployeeRepository employeeRepo;
    private final AttachmentService attachmentService;
    private final AuditService auditService;
    private final CurrentRequest currentRequest;

    public AnnualTaxCertificateService(AnnualPayrollSummaryRepository summaryRepo,
                                        AnnualTaxCertificateRepository certificateRepo,
                                        PayrollRunRepository runRepo,
                                        PayrollResultRepository resultRepo,
                                        EmployeeRepository employeeRepo,
                                        AttachmentService attachmentService,
                                        AuditService auditService,
                                        CurrentRequest currentRequest) {
        this.summaryRepo = summaryRepo;
        this.certificateRepo = certificateRepo;
        this.runRepo = runRepo;
        this.resultRepo = resultRepo;
        this.employeeRepo = employeeRepo;
        this.attachmentService = attachmentService;
        this.auditService = auditService;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public void generateSummary(int year) {
        List<PayrollRun> runs = runRepo.findAllByOrderByPeriodYearDescPeriodMonthDesc();
        List<UUID> paidRunIds = runs.stream()
                .filter(r -> r.getPeriodYear() == year)
                .filter(r -> r.getRunType() == RunType.REGULAR)
                .filter(r -> r.getStatus() == PayrollRunStatus.PAID)
                .map(PayrollRun::getId)
                .toList();

        if (paidRunIds.isEmpty()) {
            log.info("No PAID REGULAR runs found for year {}", year);
            return;
        }

        Map<UUID, List<PayrollResult>> resultsByEmployee = new HashMap<>();
        for (UUID runId : paidRunIds) {
            List<PayrollResult> results = resultRepo.findByRunIdOrderByEmployeeIdAsc(runId);
            for (PayrollResult r : results) {
                resultsByEmployee.computeIfAbsent(r.getEmployeeId(), k -> new ArrayList<>()).add(r);
            }
        }

        List<UUID> employeeIds = new ArrayList<>(resultsByEmployee.keySet());
        Map<UUID, Employee> employees = employeeRepo.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        int generatedCount = 0;
        for (Map.Entry<UUID, List<PayrollResult>> entry : resultsByEmployee.entrySet()) {
            UUID employeeId = entry.getKey();
            List<PayrollResult> empResults = entry.getValue();
            Employee emp = employees.get(employeeId);

            if (emp == null) continue;

            BigDecimal totalGross = empResults.stream()
                    .map(PayrollResult::getGrossAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalIncomeTax = empResults.stream()
                    .map(PayrollResult::getIncomeTax)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalDsmf = empResults.stream()
                    .map(PayrollResult::getDsmfEmployee)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalMmi = empResults.stream()
                    .map(PayrollResult::getMmiEmployee)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalUnemployment = empResults.stream()
                    .map(PayrollResult::getUnemplEmployee)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalBonuses = empResults.stream()
                    .map(PayrollResult::getBonusAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalNet = empResults.stream()
                    .map(PayrollResult::getNetAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            AnnualPayrollSummary summary = summaryRepo.findByEmployeeIdAndYear(employeeId, year)
                    .orElseGet(() -> {
                        AnnualPayrollSummary s = new AnnualPayrollSummary();
                        s.setEmployeeId(employeeId); // tenantId defaults to "default"
                        s.setYear(year);
                        return s;
                    });

            summary.setTotalGross(totalGross);
            summary.setTotalIncomeTax(totalIncomeTax);
            summary.setTotalDsmfEmployee(totalDsmf);
            summary.setTotalMmiEmployee(totalMmi);
            summary.setTotalUnemplEmployee(totalUnemployment);
            summary.setTotalNet(totalNet);
            summary.setTotalBonuses(totalBonuses);
            summary.setMonthsCount(empResults.size());

            summaryRepo.save(summary);
            generatedCount++;

            auditService.record("payroll", "AnnualPayrollSummary", summary.getId().toString(),
                    "GENERATED", null, "year=" + year);
        }

        log.info("Generated {} annual payroll summaries for year {}", generatedCount, year);
    }

    @Transactional
    public void generateCertificates(int year) {
        List<AnnualPayrollSummary> summaries = summaryRepo.findByYear(year);
        if (summaries.isEmpty()) {
            log.info("No annual payroll summaries found for year {}. Run generateSummary first.", year);
            return;
        }

        List<UUID> employeeIds = summaries.stream()
                .map(AnnualPayrollSummary::getEmployeeId)
                .toList();
        Map<UUID, Employee> employees = employeeRepo.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        int generatedCount = 0;
        for (AnnualPayrollSummary summary : summaries) {
            Employee emp = employees.get(summary.getEmployeeId());
            if (emp == null) continue;

            BigDecimal exemptAmount = MONTHLY_EXEMPTION.multiply(new BigDecimal(summary.getMonthsCount()));
            BigDecimal taxableGross = summary.getTotalGross().subtract(exemptAmount);
            if (taxableGross.compareTo(BigDecimal.ZERO) < 0) {
                taxableGross = BigDecimal.ZERO;
            }

            AnnualTaxCertificate cert = certificateRepo.findByEmployeeIdAndYear(summary.getEmployeeId(), year)
                    .orElseGet(() -> {
                        AnnualTaxCertificate c = new AnnualTaxCertificate();
                        c.setTenantId(summary.getTenantId());
                        c.setEmployeeId(summary.getEmployeeId());
                        c.setYear(year);
                        return c;
                    });

            cert.setAnnualGross(summary.getTotalGross());
            cert.setExemptAmount(exemptAmount);
            cert.setTaxableGross(taxableGross);
            cert.setTotalTaxWithheld(summary.getTotalIncomeTax());
            cert.setStatus(CertificateStatus.GENERATED);
            cert.setGeneratedAt(OffsetDateTime.now());
            cert.setGeneratedBy(currentRequest.username());

            certificateRepo.save(cert);

            // Generate PDF
            byte[] pdfBytes = generateCertificatePdf(cert, emp);
            String filename = String.format("Tax_Certificate_%d_%s.pdf", year, emp.getEmployeeNo());

            Attachment attachment = attachmentService.uploadBytes(
                    "payroll",
                    "annualtaxcertificate",
                    cert.getId(),
                    pdfBytes,
                    filename,
                    "application/pdf"
            );

            cert.setPdfAttachmentId(attachment.getId());
            cert.setPdfStoragePath(attachment.getObjectKey());
            certificateRepo.save(cert);

            auditService.record("payroll", "AnnualTaxCertificate", cert.getId().toString(),
                    "GENERATED", null, "year=" + year);

            generatedCount++;
        }

        log.info("Generated {} annual tax certificates with PDFs for year {}", generatedCount, year);
    }

    @Transactional(readOnly = true)
    public List<AnnualTaxCertificate> listCertificates(int year) {
        return certificateRepo.findByYear(year);
    }

    @Transactional(readOnly = true)
    public byte[] downloadCertificate(UUID certId) {
        AnnualTaxCertificate cert = certificateRepo.findById(certId)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found"));

        if (cert.getPdfAttachmentId() == null) {
            throw new ResourceNotFoundException("PDF not yet generated for this certificate");
        }

        try (var stream = attachmentService.download(cert.getPdfAttachmentId())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download certificate PDF: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public AnnualTaxCertificate employeeCertificate(UUID employeeId, int year) {
        return certificateRepo.findByEmployeeIdAndYear(employeeId, year)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No certificate found for employee " + employeeId + " and year " + year));
    }

    private byte[] generateCertificatePdf(AnnualTaxCertificate cert, Employee emp) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);

        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("ANNUAL TAX CERTIFICATE", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            doc.add(title);

            // Certificate year
            Font yearFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Paragraph yearPara = new Paragraph("Tax Year: " + cert.getYear(), yearFont);
            yearPara.setAlignment(Element.ALIGN_CENTER);
            yearPara.setSpacingAfter(30);
            doc.add(yearPara);

            // Employee details table
            PdfPTable detailsTable = new PdfPTable(2);
            detailsTable.setWidthPercentage(90);
            detailsTable.setSpacingAfter(20);
            detailsTable.setWidths(new float[]{2, 3});

            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 11);

            addDetailRow(detailsTable, "Employee Name:",
                    emp.getFirstName() + " " + emp.getLastName(), labelFont, valueFont);
            addDetailRow(detailsTable, "Employee Number:",
                    emp.getEmployeeNo(), labelFont, valueFont);
            addDetailRow(detailsTable, "Employee VÖEN (Tax ID):",
                    emp.getNationalId() != null ? emp.getNationalId() : "N/A", labelFont, valueFont);
            addDetailRow(detailsTable, "Employer VÖEN:",
                    EMPLOYER_VOEN, labelFont, valueFont);
            addDetailRow(detailsTable, "Period:",
                    "01/01/" + cert.getYear() + " - 31/12/" + cert.getYear(), labelFont, valueFont);

            doc.add(detailsTable);

            // Financial summary
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
            Paragraph summaryTitle = new Paragraph("Income & Tax Summary", sectionFont);
            summaryTitle.setSpacingBefore(10);
            summaryTitle.setSpacingAfter(10);
            doc.add(summaryTitle);

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(90);
            summaryTable.setSpacingAfter(20);
            summaryTable.setWidths(new float[]{3, 2});

            addSummaryRow(summaryTable, "Annual Gross Income:",
                    formatMoney(cert.getAnnualGross()), labelFont, valueFont);
            addSummaryRow(summaryTable, "Tax-Exempt Amount:",
                    formatMoney(cert.getExemptAmount()), labelFont, valueFont);
            addSummaryRow(summaryTable, "Taxable Income:",
                    formatMoney(cert.getTaxableGross()), labelFont, valueFont);

            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            addSummaryRow(summaryTable, "Total Income Tax Withheld:",
                    formatMoney(cert.getTotalTaxWithheld()), totalFont, totalFont);

            doc.add(summaryTable);

            // Footer
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9);
            Paragraph footer = new Paragraph(
                    "Generated on " + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) +
                    "\nThis is a computer-generated certificate and does not require a signature.",
                    footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(30);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate tax certificate PDF: " + e.getMessage(), e);
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

    private void addSummaryRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setPadding(8);
        labelCell.setBackgroundColor(new java.awt.Color(248, 248, 252));
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setPadding(8);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private String formatMoney(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toString() + " AZN";
    }
}
