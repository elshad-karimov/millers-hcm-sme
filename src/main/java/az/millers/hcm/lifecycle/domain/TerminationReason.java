package az.millers.hcm.lifecycle.domain;

/** Termination reason codes (PRD 8.11.1). */
public enum TerminationReason {
    VOLUNTARY_RESIGNATION,
    INVOLUNTARY_DISMISSAL,
    MUTUAL_AGREEMENT,
    END_OF_CONTRACT,
    RETIREMENT,
    REDUNDANCY,
    PROBATION_FAIL,
    DEATH,
    OTHER
}
