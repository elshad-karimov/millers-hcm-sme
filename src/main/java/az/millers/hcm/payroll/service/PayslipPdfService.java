package az.millers.hcm.payroll.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.email.EmailService;
import az.millers.hcm.payroll.domain.BankAccount;
import az.millers.hcm.payroll.domain.ComponentKind;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.domain.PayrollResultComponent;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollRunStatus;
import az.millers.hcm.payroll.repo.BankAccountRepository;
import az.millers.hcm.payroll.repo.PayrollResultComponentRepository;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;

@Service
public class PayslipPdfService {

    private static final Logger log = LoggerFactory.getLogger(PayslipPdfService.class);
    private static final String MODULE = "payroll";
    private static final String ENTITY = "Payslip";

    private final PayrollRunRepository runRepo;
    private final PayrollResultRepository resultRepo;
    private final PayrollResultComponentRepository componentRepo;
    private final EmployeeRepository employeeRepo;
    private final BankAccountRepository bankAccountRepo;
    private final AttachmentService attachmentService;
    private final AuditService auditService;
    private final EmailService emailService;

    public PayslipPdfService(PayrollRunRepository runRepo,
                             PayrollResultRepository resultRepo,
                             PayrollResultComponentRepository componentRepo,
                             EmployeeRepository employeeRepo,
                             BankAccountRepository bankAccountRepo,
                             AttachmentService attachmentService,
                             AuditService auditService,
                             EmailService emailService) {
        this.runRepo = runRepo;
        this.resultRepo = resultRepo;
        this.componentRepo = componentRepo;
        this.employeeRepo = employeeRepo;
        this.bankAccountRepo = bankAccountRepo;
        this.attachmentService = attachmentService;
        this.auditService = auditService;
        this.emailService = emailService;
    }

    @Transactional
    public String generate(UUID runId, UUID employeeId) {
        PayrollRun run = runRepo.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found"));

        if (run.getStatus() != PayrollRunStatus.PAID) {
            throw new BadRequestException("Payslips can only be generated for PAID runs");
        }

        PayrollResult result = resultRepo.findByRunIdAndEmployeeId(runId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll result not found for employee"));

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        List<PayrollResultComponent> components = componentRepo.findByResultIdOrderByKindDescAmountDesc(result.getId());

        byte[] pdfBytes = generatePayslipPdf(run, result, employee, components);
        String filename = String.format("Payslip_%s_%s.pdf", result.getPayslipNo(), employee.getEmployeeNo());

        // Delete existing payslip if any (idempotent)
        List<Attachment> existing = attachmentService.list(MODULE, "payslip", result.getId());
        existing.forEach(att -> attachmentService.delete(att.getId()));

        Attachment attachment = attachmentService.uploadBytes(
                MODULE,
                "payslip",
                result.getId(),
                pdfBytes,
                filename,
                "application/pdf"
        );

        auditService.record(MODULE, ENTITY, result.getId().toString(),
                "GENERATED", null, "payslipNo=" + result.getPayslipNo());

        return attachment.getObjectKey();
    }

    @Transactional
    public Map<String, Object> bulkGenerate(UUID runId) {
        PayrollRun run = runRepo.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found"));

        if (run.getStatus() != PayrollRunStatus.PAID) {
            throw new BadRequestException("Payslips can only be generated for PAID runs");
        }

        List<PayrollResult> results = resultRepo.findByRunIdOrderByEmployeeIdAsc(runId);
        int count = 0;

        for (PayrollResult result : results) {
            try {
                generate(runId, result.getEmployeeId());
                count++;
            } catch (Exception e) {
                log.warn("Failed to generate payslip for employee {}: {}", result.getEmployeeId(), e.getMessage());
            }
        }

        log.info("Bulk generated {} payslips for run {}", count, runId);
        return Map.of("generated", count, "total", results.size());
    }

    @Transactional(readOnly = true)
    public byte[] download(UUID runId, UUID employeeId) {
        PayrollResult result = resultRepo.findByRunIdAndEmployeeId(runId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll result not found"));

        List<Attachment> attachments = attachmentService.list(MODULE, "payslip", result.getId());
        if (attachments.isEmpty()) {
            throw new ResourceNotFoundException("Payslip PDF not yet generated");
        }

        try (var stream = attachmentService.download(attachments.get(0).getId())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download payslip: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> sendByEmail(UUID runId) {
        PayrollRun run = runRepo.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found"));

        List<PayrollResult> results = resultRepo.findByRunIdOrderByEmployeeIdAsc(runId);
        int sent = 0;
        List<String> failed = new ArrayList<>();

        for (PayrollResult result : results) {
            Employee emp = employeeRepo.findById(result.getEmployeeId()).orElse(null);
            if (emp == null || emp.getWorkEmail() == null || emp.getWorkEmail().isBlank()) {
                log.warn("Skipping payslip email for employee {} (no work email)", result.getEmployeeId());
                continue;
            }

            try {
                byte[] pdfBytes = download(runId, result.getEmployeeId());
                String filename = String.format("Payslip_%s.pdf", result.getPayslipNo());
                String subject = String.format("Payslip %s - %d/%02d", result.getPayslipNo(), run.getPeriodYear(), run.getPeriodMonth());
                String body = String.format("<p>Dear %s,</p><p>Please find attached your payslip for %d/%02d.</p>",
                        emp.getFirstName(), run.getPeriodYear(), run.getPeriodMonth());

                emailService.sendReport(List.of(emp.getWorkEmail()), subject, body, filename, pdfBytes, "application/pdf");
                sent++;

                auditService.record(MODULE, ENTITY, result.getId().toString(),
                        "EMAIL_SENT", null, "to=" + emp.getWorkEmail());
            } catch (Exception e) {
                log.warn("Failed to email payslip to {}: {}", emp.getWorkEmail(), e.getMessage());
                failed.add(emp.getEmployeeNo() + ": " + e.getMessage());
            }
        }

        log.info("Sent {} payslip emails for run {}", sent, runId);
        return Map.of("sent", sent, "failed", failed);
    }

    private byte[] generatePayslipPdf(PayrollRun run, PayrollResult result, Employee employee, List<PayrollResultComponent> components) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);

        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // Header
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Paragraph title = new Paragraph("PAYSLIP", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            doc.add(title);

            Font periodFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            Paragraph period = new Paragraph(
                    String.format("Period: %d/%02d  |  Payslip No: %s",
                            run.getPeriodYear(), run.getPeriodMonth(), result.getPayslipNo()),
                    periodFont);
            period.setAlignment(Element.ALIGN_CENTER);
            period.setSpacingAfter(20);
            doc.add(period);

            // Employee details
            PdfPTable detailsTable = new PdfPTable(2);
            detailsTable.setWidthPercentage(90);
            detailsTable.setSpacingAfter(20);
            detailsTable.setWidths(new float[]{2, 3});

            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            addDetailRow(detailsTable, "Employee No:", employee.getEmployeeNo(), labelFont, valueFont);
            addDetailRow(detailsTable, "Name:", employee.getFirstName() + " " + employee.getLastName(), labelFont, valueFont);
            addDetailRow(detailsTable, "Position:", employee.getPositionTitle() != null ? employee.getPositionTitle() : "N/A", labelFont, valueFont);

            doc.add(detailsTable);

            // Earnings section
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Paragraph earningsTitle = new Paragraph("Earnings", sectionFont);
            earningsTitle.setSpacingBefore(10);
            earningsTitle.setSpacingAfter(5);
            doc.add(earningsTitle);

            PdfPTable earningsTable = new PdfPTable(2);
            earningsTable.setWidthPercentage(90);
            earningsTable.setWidths(new float[]{3, 2});

            addAmountRow(earningsTable, "Base Salary", result.getBaseSalary(), valueFont);
            for (PayrollResultComponent comp : components) {
                if (comp.getKind() == ComponentKind.EARNING) {
                    addAmountRow(earningsTable, comp.getComponentName(), comp.getAmount(), valueFont);
                }
            }
            if (result.getBonusAmount().compareTo(BigDecimal.ZERO) > 0) {
                addAmountRow(earningsTable, "Bonuses", result.getBonusAmount(), valueFont);
            }
            if (result.getAllowanceAmount().compareTo(BigDecimal.ZERO) > 0) {
                addAmountRow(earningsTable, "Allowances", result.getAllowanceAmount(), valueFont);
            }
            if (result.getOvertimePay().compareTo(BigDecimal.ZERO) > 0) {
                addAmountRow(earningsTable, "Overtime Pay", result.getOvertimePay(), valueFont);
            }

            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            addAmountRow(earningsTable, "Gross Pay", result.getGrossAmount(), totalFont);

            doc.add(earningsTable);
            doc.add(new Paragraph(" "));

            // Deductions section
            Paragraph deductionsTitle = new Paragraph("Deductions", sectionFont);
            deductionsTitle.setSpacingAfter(5);
            doc.add(deductionsTitle);

            PdfPTable deductionsTable = new PdfPTable(2);
            deductionsTable.setWidthPercentage(90);
            deductionsTable.setWidths(new float[]{3, 2});

            addAmountRow(deductionsTable, "Income Tax", result.getIncomeTax(), valueFont);
            addAmountRow(deductionsTable, "DSMF (Employee)", result.getDsmfEmployee(), valueFont);
            addAmountRow(deductionsTable, "MMI (Employee)", result.getMmiEmployee(), valueFont);
            addAmountRow(deductionsTable, "Unemployment (Employee)", result.getUnemplEmployee(), valueFont);

            for (PayrollResultComponent comp : components) {
                if (comp.getKind() == ComponentKind.DEDUCTION) {
                    addAmountRow(deductionsTable, comp.getComponentName(), comp.getAmount(), valueFont);
                }
            }

            if (result.getDeductionAmount().compareTo(BigDecimal.ZERO) > 0) {
                addAmountRow(deductionsTable, "Other Deductions", result.getDeductionAmount(), valueFont);
            }

            doc.add(deductionsTable);
            doc.add(new Paragraph(" "));

            // Net pay
            PdfPTable netTable = new PdfPTable(2);
            netTable.setWidthPercentage(90);
            netTable.setWidths(new float[]{3, 2});
            Font netFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            addAmountRow(netTable, "NET PAY", result.getNetAmount(), netFont);
            doc.add(netTable);
            doc.add(new Paragraph(" "));

            // Employer contributions (informational)
            Paragraph employerTitle = new Paragraph("Employer Contributions (Informational)", sectionFont);
            employerTitle.setSpacingBefore(10);
            employerTitle.setSpacingAfter(5);
            doc.add(employerTitle);

            PdfPTable employerTable = new PdfPTable(2);
            employerTable.setWidthPercentage(90);
            employerTable.setWidths(new float[]{3, 2});

            addAmountRow(employerTable, "DSMF (Employer)", result.getDsmfEmployer(), valueFont);
            addAmountRow(employerTable, "MMI (Employer)", result.getMmiEmployer(), valueFont);
            addAmountRow(employerTable, "Unemployment (Employer)", result.getUnemplEmployer(), valueFont);

            doc.add(employerTable);
            doc.add(new Paragraph(" "));

            // Bank details
            BankAccount bankAccount = bankAccountRepo.findByEmployeeId(employee.getId()).orElse(null);
            if (bankAccount != null && bankAccount.getIban() != null) {
                Paragraph bankTitle = new Paragraph("Payment Details", sectionFont);
                bankTitle.setSpacingBefore(10);
                bankTitle.setSpacingAfter(5);
                doc.add(bankTitle);

                PdfPTable bankTable = new PdfPTable(2);
                bankTable.setWidthPercentage(90);
                bankTable.setWidths(new float[]{2, 3});

                addDetailRow(bankTable, "IBAN:", maskIban(bankAccount.getIban()), labelFont, valueFont);

                doc.add(bankTable);
            }

            // Footer
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9);
            Paragraph footer = new Paragraph(
                    "Generated on " + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) +
                    "\nThis is a computer-generated payslip and does not require a signature.",
                    footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(20);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate payslip PDF: " + e.getMessage(), e);
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

    private String maskIban(String iban) {
        if (iban == null || iban.length() < 4) {
            return "****";
        }
        return "****" + iban.substring(iban.length() - 4);
    }
}
