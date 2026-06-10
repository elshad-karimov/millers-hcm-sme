package az.millers.hcm.recruitment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import az.millers.hcm.recruitment.api.dto.VacancyRequest;
import az.millers.hcm.staffing.event.HeadcountIncreasedEvent;

/**
 * Auto-posts a vacancy to Recruitment when a headcount-increase request is
 * approved (M198 / PRD §8.3.7 AC: "posts them to Recruitment").
 *
 * <p>The new vacancy is seeded from the position's title, salary range, location,
 * and org-unit label. It is created as OPEN with {@code openings = delta} so
 * recruiters can immediately start sourcing candidates.
 *
 * <p>{@code @Async} ensures the vacancy creation runs <em>after</em> the
 * headcount-change transaction commits — this is required so that
 * {@link VacancyService#create} sees the updated {@code approvedHeadcount}
 * when it calls {@code assertCanPostVacancy}.
 */
@Component
public class VacancyAutoPostListener {

    private static final Logger log = LoggerFactory.getLogger(VacancyAutoPostListener.class);

    private final VacancyService vacancyService;

    public VacancyAutoPostListener(VacancyService vacancyService) {
        this.vacancyService = vacancyService;
    }

    @Async
    @EventListener
    public void onHeadcountIncreased(HeadcountIncreasedEvent event) {
        try {
            VacancyRequest req = new VacancyRequest(
                    event.positionTitle(),
                    event.positionId(),
                    event.orgUnitLabel(),
                    event.location(),
                    event.delta(),
                    null,  // description — HR fills in after creation
                    null,  // requirements — HR fills in after creation
                    event.salaryMin(),
                    event.salaryMax(),
                    event.currency(),
                    null,  // hiringManagerId — to be assigned
                    null,  // recruiterId — to be assigned
                    null,  // openingDate
                    null,  // closingDate
                    // M274 — auto-posted vacancies are new headcount by
                    // definition (they fire on headcount increase).
                    az.millers.hcm.recruitment.domain.RequisitionType.NEW_HEADCOUNT,
                    az.millers.hcm.recruitment.domain.HiringReason.DEPARTMENT_EXPANSION,
                    null,  // targetStartDate
                    null,  // costCentre
                    null,  // employmentType
                    null); // replacedEmployeeId
            var v = vacancyService.create(req);
            log.info("VacancyAutoPostListener: auto-created vacancy {} ({} opening(s)) "
                    + "for position {} approved by {}",
                    v.getVacancyNo(), event.delta(), event.positionCode(), event.approvedBy());
        } catch (Exception ex) {
            log.warn("VacancyAutoPostListener: failed to auto-post vacancy "
                    + "for position {} (delta={}): {}",
                    event.positionCode(), event.delta(), ex.getMessage());
        }
    }
}
