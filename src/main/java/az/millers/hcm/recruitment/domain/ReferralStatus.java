package az.millers.hcm.recruitment.domain;

/**
 * M295 — Recruitment PRD Phase F: employee-referral lifecycle.
 * SUBMITTED → HIRED → QUALIFIED → PAID, with REJECTED as an off-ramp.
 */
public enum ReferralStatus {
    SUBMITTED, HIRED, QUALIFIED, PAID, REJECTED
}
