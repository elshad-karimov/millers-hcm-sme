package az.millers.hcm.notifications.domain;

/**
 * Stable category keys used by {@link NotificationLog} and the M115
 * preferences table.
 *
 * <p>Each value corresponds to a single class of notification that a user
 * can mute independently. Categories are deliberately broader than the
 * specific event so a user can mute "report deliveries" without losing
 * the ability to receive any specific run individually.
 *
 * <p><b>Adding a new category:</b> add the enum constant, set
 * {@link #displayName} + {@link #description}, then wire the new sender
 * to pass it through {@code NotificationService.notifyAll(category, ...)}.
 * No migration is required — the table is keyed by free-text strings.
 *
 * <p><b>{@link #TRANSACTIONAL}</b> is a sentinel for synchronous,
 * action-driven notifications (workflow approval requests, password
 * resets, security alerts) that the user cannot opt out of. The
 * preferences service returns {@code true} unconditionally for it.
 */
public enum NotificationCategory {

    /** Approvals you must act on — workflow steps, signature requests. */
    WORKFLOW_APPROVAL("Approval requests",
            "Workflow approvals you need to act on — leave, business trips, contract changes, etc."),
    /** A submitter being told their request was approved / rejected. */
    WORKFLOW_OUTCOME("Approval outcomes",
            "Notifications when a workflow you submitted is approved or rejected."),
    /** Expiry-driven alerts (certifications, contracts, identity docs). */
    EXPIRY_ALERT("Document expiry",
            "Reminders about expiring certifications, contracts, and identity documents."),
    /** Probation review reminders. */
    PROBATION_REVIEW("Probation reviews",
            "Reminders to complete probation reviews for direct reports."),
    /** Learning path / mandatory course nudges. */
    LEARNING_REMINDER("Learning reminders",
            "Nudges about pending learning paths and mandatory courses."),
    /** Course assignment notifications — new course or path assigned to you. */
    COURSE_ASSIGNMENT("Course assignments",
            "Notifications when a new course or learning path is assigned to you."),
    /** Course completion, failure, and certificate notifications. */
    LEARNING_COMPLETION("Course completions & certificates",
            "Notifications when you pass or fail a course, or when a certificate is issued."),
    /** Termination processed — notifies the line manager, IT, and Finance. */
    TERMINATION_NOTICE("Termination notices",
            "Notifications when an employee termination is processed — sent to the manager, IT, and Finance."),
    /** Recruiter reminders about candidates that have gone cold. */
    STALE_POOL_REMINDER("Stale candidate reminders",
            "Daily digest of candidates in your pipeline that haven't been contacted recently."),
    /** Scheduled report delivery (emailed XLSX/PDF). */
    REPORT_DELIVERY("Report deliveries",
            "Scheduled reports delivered to your inbox."),
    /** General announcements / digests from HR or admin. */
    ANNOUNCEMENT("Announcements",
            "Platform-wide announcements and HR communications."),
    /**
     * Mandatory transactional notifications — security alerts, password
     * resets, urgent workflow escalations. The preference service ignores
     * the user's opt-out for these.
     */
    TRANSACTIONAL("Transactional (cannot mute)",
            "Mandatory operational notifications — security alerts, urgent escalations.");

    private final String displayName;
    private final String description;

    NotificationCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }

    /** True iff the user is allowed to opt out of this category. */
    public boolean isMutable() {
        return this != TRANSACTIONAL;
    }
}
