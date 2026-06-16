package az.millers.hcm.lifecycle.domain;

/** Lifecycle of an onboarding resource request (M301 — Phase B.1). */
public enum ResourceRequestStatus {
    REQUESTED,
    IN_PROGRESS,
    FULFILLED,
    CANCELLED
}
