package az.millers.hcm.ehs.domain;

/**
 * M450 — Computed risk band: LOW <5, MEDIUM 5-14, HIGH ≥15.
 */
public enum RiskBand {
    LOW,
    MEDIUM,
    HIGH;

    public static RiskBand fromScore(int riskScore) {
        if (riskScore < 5) return LOW;
        if (riskScore < 15) return MEDIUM;
        return HIGH;
    }
}
