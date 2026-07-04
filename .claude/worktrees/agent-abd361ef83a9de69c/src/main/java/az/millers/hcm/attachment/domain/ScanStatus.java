package az.millers.hcm.attachment.domain;

/**
 * Virus-scan outcome for an {@link Attachment} (M50 — PRD 14.8).
 *
 * <ul>
 *   <li>{@code PENDING}  — row created; ClamAV scan not yet attempted.</li>
 *   <li>{@code CLEAN}    — ClamAV confirmed no threat.</li>
 *   <li>{@code INFECTED} — ClamAV flagged malware; the upload is normally
 *       rejected before the row is committed, so this value is a safety net
 *       for administrative overrides only.</li>
 *   <li>{@code SKIPPED}  — scan intentionally skipped (server-generated payloads
 *       like report exports, or {@code HCM_VIRUS_SCAN_ENABLED=false}).</li>
 *   <li>{@code ERROR}    — scanner was reachable but returned an unexpected
 *       response; treat as untrusted.</li>
 * </ul>
 */
public enum ScanStatus {
    PENDING,
    CLEAN,
    INFECTED,
    SKIPPED,
    ERROR
}
