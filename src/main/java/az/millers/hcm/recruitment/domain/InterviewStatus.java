package az.millers.hcm.recruitment.domain;

/**
 * Interview lifecycle (M85). Mirrors the V66 CHECK enum.
 *
 * <pre>
 *   SCHEDULED ──start──► IN_PROGRESS ──finalize──► COMPLETED
 *      │                      │
 *      └──────cancel──────────┴──► CANCELLED   (terminal)
 *                                  NO_SHOW     (terminal — candidate didn't attend)
 * </pre>
 */
public enum InterviewStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}
