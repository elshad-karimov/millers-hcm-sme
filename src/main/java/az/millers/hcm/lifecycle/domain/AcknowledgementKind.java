package az.millers.hcm.lifecycle.domain;

import java.util.Optional;

/**
 * What kind of onboarding acknowledgement was recorded (M304 — Phase B.4).
 * Derived from the originating {@link ChecklistTaskType}.
 */
public enum AcknowledgementKind {
    POLICY,
    CONTRACT,
    DOCUMENT;

    /** The acknowledgement kind a task type produces, or empty if the type isn't acknowledgeable. */
    public static Optional<AcknowledgementKind> forTaskType(ChecklistTaskType type) {
        if (type == null) return Optional.empty();
        return switch (type) {
            case POLICY_ACKNOWLEDGEMENT -> Optional.of(POLICY);
            case CONTRACT_SIGNING -> Optional.of(CONTRACT);
            case DOCUMENT_COLLECTION -> Optional.of(DOCUMENT);
            default -> Optional.empty();
        };
    }
}
