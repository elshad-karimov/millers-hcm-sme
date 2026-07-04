package az.millers.hcm.lifecycle.domain;

/**
 * Onboarding/offboarding task type (M299 — Onboarding Phase A.2, PRD §2).
 *
 * <p>Classifies what a checklist task <em>is</em>, which drives three things:
 * the default responsible role (see {@link #defaultOwnerRole()}), the persona
 * dashboard it surfaces on (Phase E), and — from Phase B onward — the
 * downstream action the task triggers (equipment → asset request, IT account →
 * provisioning ticket, training → LMS enrolment, etc.). Until Phase B wires
 * those actions, the type is a classification + routing + grouping dimension.
 */
public enum ChecklistTaskType {

    DOCUMENT_COLLECTION("HR"),
    CONTRACT_SIGNING("HR"),
    POLICY_ACKNOWLEDGEMENT("HR"),
    EQUIPMENT_REQUEST("IT"),
    IT_ACCOUNT_REQUEST("IT"),
    WORKSPACE_REQUEST("FACILITIES"),
    TRAINING_ASSIGNMENT("HR"),
    MEETING_SCHEDULE("MANAGER"),
    MANAGER_TASK("MANAGER"),
    HR_TASK("HR"),
    PAYROLL_TASK("FINANCE"),
    SECURITY_ACCESS_TASK("SECURITY"),
    BUDDY_TASK("MANAGER"),
    MANUAL_TASK(null),
    APPROVAL_TASK("MANAGER");

    private final String defaultOwnerRole;

    ChecklistTaskType(String defaultOwnerRole) {
        this.defaultOwnerRole = defaultOwnerRole;
    }

    /**
     * The natural responsible role for this task type, or {@code null} for
     * {@link #MANUAL_TASK} (no implied owner). Used at start time to fill a
     * task's owner when the template didn't set one explicitly — an explicit
     * {@code defaultOwnerRole} on the template task always wins.
     */
    public String defaultOwnerRole() {
        return defaultOwnerRole;
    }
}
