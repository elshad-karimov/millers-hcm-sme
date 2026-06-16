package az.millers.hcm.lifecycle.domain;

import java.util.Optional;

/**
 * Category of an onboarding resource (provisioning) request (M301 — Phase B.1).
 *
 * <p>Derived from the originating {@link ChecklistTaskType}. Phase B.1 wires the
 * EQUIPMENT and WORKSPACE categories; IT_ACCOUNT / ACCESS_CARD are present so
 * Phase B.2 can reuse the same request entity by extending
 * {@link #forTaskType(ChecklistTaskType)}.
 */
public enum ResourceRequestCategory {
    EQUIPMENT,
    WORKSPACE,
    IT_ACCOUNT,
    ACCESS_CARD,
    OTHER;

    /**
     * The request category a checklist task type auto-spawns, or empty if the
     * type isn't a provisioning request. Phase B.1 maps EQUIPMENT_REQUEST and
     * WORKSPACE_REQUEST; B.2 will add IT_ACCOUNT_REQUEST / SECURITY_ACCESS_TASK.
     */
    public static Optional<ResourceRequestCategory> forTaskType(ChecklistTaskType type) {
        if (type == null) return Optional.empty();
        return switch (type) {
            case EQUIPMENT_REQUEST -> Optional.of(EQUIPMENT);
            case WORKSPACE_REQUEST -> Optional.of(WORKSPACE);
            default -> Optional.empty();
        };
    }
}
