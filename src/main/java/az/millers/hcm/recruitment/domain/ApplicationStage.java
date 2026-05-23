package az.millers.hcm.recruitment.domain;

/**
 * Default pipeline stages (PRD 8.10.3). Per-vacancy override is a
 * later-work seam — for MVP all vacancies use this fixed pipeline.
 */
public enum ApplicationStage {
    CV_SCREENING,
    HR_INTERVIEW,
    TECHNICAL_INTERVIEW,
    FINAL_INTERVIEW,
    OFFER,
    HIRED,
    REJECTED,
    WITHDRAWN;

    public boolean isTerminal() {
        return this == HIRED || this == REJECTED || this == WITHDRAWN;
    }
}
