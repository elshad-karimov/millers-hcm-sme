package az.millers.hcm.employeerelations.domain;

/**
 * M446 — Warning levels with expiry defaults.
 */
public enum WarningLevel {
    VERBAL(6),         // 6 months
    FIRST_WRITTEN(12), // 12 months
    SECOND_WRITTEN(18),// 18 months
    FINAL(null);       // indefinite

    private final Integer defaultExpiryMonths;

    WarningLevel(Integer defaultExpiryMonths) {
        this.defaultExpiryMonths = defaultExpiryMonths;
    }

    public Integer getDefaultExpiryMonths() {
        return defaultExpiryMonths;
    }
}
