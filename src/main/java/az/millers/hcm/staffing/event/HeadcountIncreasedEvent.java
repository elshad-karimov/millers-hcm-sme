package az.millers.hcm.staffing.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published when a headcount-change request with a positive delta is approved
 * (M198 / PRD §8.3.7 AC: "posts them to Recruitment").
 *
 * <p>Consumed by
 * {@link az.millers.hcm.recruitment.service.VacancyAutoPostListener}
 * to auto-create a vacancy so Recruitment can start filling the new openings
 * immediately.
 *
 * @param positionId    the staffing position whose approved headcount was raised
 * @param delta         the number of new openings (always {@code > 0})
 * @param positionCode  position code (e.g. "ENG-SR-001")
 * @param positionTitle position title to use as the vacancy title
 * @param orgUnitLabel  org-unit / department name, may be null
 * @param location      work location, may be null
 * @param salaryMin     position salary min, may be null
 * @param salaryMax     position salary max, may be null
 * @param currency      AZN / USD / EUR (null → "AZN")
 * @param approvedBy    username of the approver
 */
public record HeadcountIncreasedEvent(
        UUID positionId,
        int delta,
        String positionCode,
        String positionTitle,
        String orgUnitLabel,
        String location,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        String approvedBy) {
}
