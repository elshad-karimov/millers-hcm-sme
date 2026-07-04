package az.millers.hcm.selfservice.domain;

public enum ServiceRequestPriority {
    LOW,
    NORMAL,
    HIGH;

    /** SLA in business days (skip weekends; holidays optional). */
    public int slaBusinessDays() {
        return switch (this) {
            case HIGH -> 1;
            case NORMAL -> 2;
            case LOW -> 5;
        };
    }
}
