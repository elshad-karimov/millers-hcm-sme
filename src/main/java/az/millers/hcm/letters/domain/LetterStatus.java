package az.millers.hcm.letters.domain;

/**
 * Lifecycle states for {@link LetterRequest} (M77 / P2-17).
 *
 * <p>Transitions:
 * <pre>
 *   (created)
 *      ↓
 *    DRAFT ──submit──► PENDING ──approve──► APPROVED ──render──► ISSUED
 *      │                  │
 *      │                  └─reject──► REJECTED
 *      │
 *      └─cancel──► CANCELLED
 * </pre>
 *
 * Auto-approve templates skip {@code PENDING} / {@code APPROVED} and go
 * straight to {@code ISSUED}.
 */
public enum LetterStatus {
    DRAFT,
    PENDING,
    APPROVED,
    ISSUED,
    REJECTED,
    CANCELLED
}
