package az.millers.hcm.reporting.export;

import java.util.ArrayList;
import java.util.List;

public class ReportTable {

    private final String title;
    private final List<String> headers;
    private final List<List<Object>> rows = new ArrayList<>();

    public ReportTable(String title, List<String> headers) {
        this.title = title;
        this.headers = headers;
    }

    public String title() { return title; }
    public List<String> headers() { return headers; }
    public List<List<Object>> rows() { return rows; }

    public ReportTable addRow(Object... cells) {
        rows.add(List.of(cells == null ? new Object[0] : cells));
        return this;
    }
}
