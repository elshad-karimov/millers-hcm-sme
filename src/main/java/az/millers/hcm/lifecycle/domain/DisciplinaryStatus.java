package az.millers.hcm.lifecycle.domain;

/**
 * State machine for {@link DisciplinaryAction} (M67 / P1-12).
 *
 * <p>Transitions:
 * <pre>
 *   DRAFT     → PENDING  (submit() — workflow started)
 *   PENDING   → APPROVED (workflow approved)
 *   PENDING   → REJECTED (workflow rejected — terminal)
 *   APPROVED  → ISSUED   (HR delivered the action to the employee)
 *   ISSUED    → APPEALED (employee filed an appeal)
 *   ISSUED    → CLOSED   (no appeal; or appeal resolved)
 *   APPEALED  → CLOSED   (appeal resolved)
 *   DRAFT     → REJECTED (cancelled before submission — rare)
 * </pre>
 */
public enum DisciplinaryStatus {
    DRAFT,
    PENDING,
    APPROVED,
    ISSUED,
    APPEALED,
    CLOSED,
    REJECTED
}
