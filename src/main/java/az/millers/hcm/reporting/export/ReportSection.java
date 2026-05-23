package az.millers.hcm.reporting.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Format-agnostic descriptor for a single section of a report. Lets the PDF
 * and XLSX renderers share the same data-shaping code: a per-report builder
 * fills in {@link #summary} (headline stats) and {@link #tables}
 * (per-row breakdowns), and each renderer walks the structure on its own
 * terms.
 */
public class ReportSection {

    private final String title;
    private final Map<String, String> summary = new LinkedHashMap<>();
    private final List<ReportTable> tables = new ArrayList<>();

    public ReportSection(String title) {
        this.title = title;
    }

    public String title() { return title; }
    public Map<String, String> summary() { return summary; }
    public List<ReportTable> tables() { return tables; }

    public ReportSection withSummary(String label, Object value) {
        summary.put(label, value == null ? "—" : value.toString());
        return this;
    }

    public ReportSection withTable(ReportTable t) {
        tables.add(t);
        return this;
    }
}
