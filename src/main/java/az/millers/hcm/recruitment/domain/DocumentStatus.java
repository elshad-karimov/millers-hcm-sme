package az.millers.hcm.recruitment.domain;

/**
 * M292 — Recruitment PRD §12: lifecycle of one candidate document.
 * PENDING → RECEIVED → VERIFIED, with REJECTED / WAIVED as off-ramps.
 */
public enum DocumentStatus {
    PENDING, RECEIVED, VERIFIED, REJECTED, WAIVED
}
