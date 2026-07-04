package az.millers.hcm.reporting.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import az.millers.hcm.reporting.domain.ReportFormat;
import az.millers.hcm.reporting.domain.ReportType;
import az.millers.hcm.reporting.export.PdfReportRenderer;
import az.millers.hcm.reporting.export.ReportSection;
import az.millers.hcm.reporting.export.ReportSectionBuilder;
import az.millers.hcm.reporting.export.XlsxReportRenderer;

/**
 * Renders a {@link ReportType} (with optional parameters) into a downloadable
 * byte[] in PDF or XLSX. Pure pass-through to the per-format renderer; the
 * report data is shaped once by {@link ReportSectionBuilder} and consumed
 * by both formats.
 */
@Service
public class ReportExportService {

    private final ReportSectionBuilder sectionBuilder;
    private final PdfReportRenderer pdfRenderer;
    private final XlsxReportRenderer xlsxRenderer;

    public ReportExportService(ReportSectionBuilder sectionBuilder,
                                PdfReportRenderer pdfRenderer,
                                XlsxReportRenderer xlsxRenderer) {
        this.sectionBuilder = sectionBuilder;
        this.pdfRenderer = pdfRenderer;
        this.xlsxRenderer = xlsxRenderer;
    }

    public byte[] export(ReportType type, ReportFormat format, Map<String, Object> params) {
        String title = humanTitle(type);
        List<ReportSection> sections = sectionBuilder.build(type, params);
        return switch (format) {
            case PDF -> pdfRenderer.render(title, sections);
            case XLSX -> xlsxRenderer.render(title, sections);
        };
    }

    public String humanTitle(ReportType type) {
        return switch (type) {
            case HEADCOUNT   -> "Headcount report";
            case ATTRITION   -> "Attrition report";
            case PAYROLL     -> "Payroll summary";
            case LEAVE       -> "Leave usage";
            case ATTENDANCE  -> "Attendance report";
            case TRAINING    -> "Training compliance";
            case PERFORMANCE -> "Performance distribution";
            case RECRUITMENT -> "Recruitment funnel";
        };
    }
}
