package az.millers.hcm.corehr.domain;

/**
 * Marital status (PRD §8.1, Phase 1 / P1-18).
 *
 * <p>Required for Azerbaijani income tax deduction calculation (married taxpayers
 * with dependants attract a higher non-taxable threshold under the Tax Code) and
 * for survivor-benefit eligibility on the death-in-service path.
 *
 * <p>{@code OTHER} is a catch-all for jurisdictions that recognise statuses
 * outside this enum (e.g. separated, annulled) without forcing the enum to grow
 * indefinitely. {@code CIVIL_PARTNERSHIP} is distinguished from {@code MARRIED}
 * for jurisdictions where the two have different statutory entitlements.
 */
public enum MaritalStatus {
    SINGLE,
    MARRIED,
    DIVORCED,
    WIDOWED,
    CIVIL_PARTNERSHIP,
    OTHER
}
