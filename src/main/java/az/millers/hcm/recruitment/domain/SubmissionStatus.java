package az.millers.hcm.recruitment.domain;

/**
 * M296 — agency candidate-submission lifecycle. ACTIVE holds the ownership
 * window; EXPIRED once it lapses; HIRED when the owned candidate is hired;
 * WITHDRAWN if the agency / HR retracts it.
 */
public enum SubmissionStatus {
    ACTIVE, EXPIRED, HIRED, WITHDRAWN
}
