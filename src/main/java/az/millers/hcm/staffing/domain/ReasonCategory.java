package az.millers.hcm.staffing.domain;

/** M259 / PRD §22 — categories of reason master used across the staffing module. */
public enum ReasonCategory {
    /** Why a position became vacant. */
    VACANCY,
    /** Why a position was frozen (M243 lifecycle). */
    FREEZE,
    /** Why a position was closed (M243 lifecycle). */
    CLOSURE,
    /** Why a replacement workflow was triggered (M246). */
    REPLACEMENT
}
