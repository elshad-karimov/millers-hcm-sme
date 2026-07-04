package az.millers.hcm.reporting.domain;

public enum ReportFormat {
    PDF,
    XLSX;

    public String contentType() {
        return switch (this) {
            case PDF -> "application/pdf";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        };
    }

    public String extension() {
        return switch (this) {
            case PDF -> "pdf";
            case XLSX -> "xlsx";
        };
    }
}
