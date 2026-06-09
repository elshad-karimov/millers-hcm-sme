package az.millers.hcm.staffing.domain;

/**
 * Workforce-plan scenario type (M247).
 *
 * <p>BASELINE is the "current planned trajectory". The rest are forks
 * off a baseline for what-if modelling. Only one BASELINE per legal
 * entity is ACTIVE at a time (enforced at the service layer).
 */
public enum ScenarioType {
    /** The canonical plan. Approved baselines become the new ACTIVE plan. */
    BASELINE,
    /** Growth / new locations / new lines of business. */
    EXPANSION,
    /** Reductions, layoffs, attrition modelling. */
    REDUCTION,
    /** Re-org, consolidation, role changes. */
    RESTRUCTURE,
    /** Seasonal capacity (holiday hiring, summer workforce). */
    SEASONAL,
    /** Free-form modelling — not intended for approval. */
    WHAT_IF
}
