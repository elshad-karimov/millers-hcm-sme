package az.millers.hcm.budgeting.api.dto;

public enum VarianceStatus {
    UNDER,      // < 90%
    WARNING,    // 90-100%
    OVER        // > 100%
}
