package az.millers.hcm.recruitment.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.EmployeeRequest;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.service.EmployeeService;
import az.millers.hcm.recruitment.api.dto.ApplicationResponse;
import az.millers.hcm.recruitment.api.dto.StageTransitionRequest;
import az.millers.hcm.recruitment.domain.Application;
import az.millers.hcm.recruitment.domain.ApplicationEvent;
import az.millers.hcm.recruitment.domain.ApplicationStage;
import az.millers.hcm.recruitment.domain.ApplicationStatus;
import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.EventType;
import az.millers.hcm.recruitment.domain.Offer;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.domain.VacancyStatus;
import az.millers.hcm.recruitment.repo.ApplicationEventRepository;
import az.millers.hcm.recruitment.repo.ApplicationRepository;
import az.millers.hcm.recruitment.repo.CandidateRepository;
import az.millers.hcm.recruitment.repo.OfferRepository;
import az.millers.hcm.recruitment.repo.VacancyRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.service.StaffingService;

/**
 * Pipeline lifecycle.
 *
 * <p>Default stage chain (PRD 8.10.3):
 * {@code CV_SCREENING → HR_INTERVIEW → TECHNICAL_INTERVIEW → FINAL_INTERVIEW → OFFER → HIRED}.
 *
 * <p>HIRED is the terminal happy-path (PRD 8.10.8): we create an Employee in
 * status {@code ON_PROBATION}, increment the linked position's occupied
 * headcount, and record the back-link.
 */
@Service
public class ApplicationService {

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "Application";

    private final ApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final VacancyRepository vacancies;
    private final CandidateRepository candidates;
    private final OfferRepository offers;
    private final EmployeeService employeeService;
    private final StaffingService staffingService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ApplicationService(ApplicationRepository applications,
                               ApplicationEventRepository events,
                               VacancyRepository vacancies,
                               CandidateRepository candidates,
                               OfferRepository offers,
                               EmployeeService employeeService,
                               StaffingService staffingService,
                               AuditService audit,
                               CurrentRequest currentRequest) {
        this.applications = applications;
        this.events = events;
        this.vacancies = vacancies;
        this.candidates = candidates;
        this.offers = offers;
        this.employeeService = employeeService;
        this.staffingService = staffingService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public Application get(UUID id) {
        return applications.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Application> forVacancy(UUID vacancyId) {
        return applications.findByVacancyIdOrderByCreatedAtAsc(vacancyId);
    }

    @Transactional(readOnly = true)
    public List<Application> forCandidate(UUID candidateId) {
        return applications.findByCandidateIdOrderByCreatedAtDesc(candidateId);
    }

    @Transactional(readOnly = true)
    public List<ApplicationEvent> historyOf(UUID applicationId) {
        return events.findByApplicationIdOrderByCreatedAtAsc(applicationId);
    }

    @Transactional
    public Application apply(UUID vacancyId, UUID candidateId) {
        Vacancy v = vacancies.findById(vacancyId)
                .orElseThrow(() -> new BadRequestException("Vacancy not found: " + vacancyId));
        if (v.getStatus() != VacancyStatus.OPEN) {
            throw new BadRequestException("Vacancy is not open: " + v.getStatus());
        }
        if (!candidates.existsById(candidateId)) {
            throw new BadRequestException("Candidate not found: " + candidateId);
        }
        Application a = new Application();
        a.setApplicationNo(String.format("APP-%05d", applications.nextNoSequence()));
        a.setVacancyId(vacancyId);
        a.setCandidateId(candidateId);
        a.setCurrentStage(ApplicationStage.CV_SCREENING);
        a.setStatus(ApplicationStatus.IN_PROGRESS);
        a.setCreatedBy(currentRequest.username());
        Application saved = applications.save(a);

        recordEvent(saved.getId(), EventType.STAGE_CHANGE, null,
                ApplicationStage.CV_SCREENING, null, null, "Application created");
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "APPLY", null, ApplicationResponse.from(saved));
        return saved;
    }

    @Transactional
    public Application transition(UUID applicationId, StageTransitionRequest req) {
        Application a = get(applicationId);
        if (a.getStatus() != ApplicationStatus.IN_PROGRESS) {
            throw new BadRequestException("Application is " + a.getStatus()
                    + " — cannot transition");
        }
        ApplicationStage from = a.getCurrentStage();
        ApplicationStage to = req.toStage();

        if (to == ApplicationStage.HIRED) {
            return hire(a, req);
        }
        if (to == ApplicationStage.REJECTED) {
            a.setCurrentStage(ApplicationStage.REJECTED);
            a.setStatus(ApplicationStatus.REJECTED);
        } else if (to == ApplicationStage.WITHDRAWN) {
            a.setCurrentStage(ApplicationStage.WITHDRAWN);
            a.setStatus(ApplicationStatus.WITHDRAWN);
        } else {
            a.setCurrentStage(to);
        }
        Application saved = applications.save(a);

        recordEvent(applicationId,
                req.rating() != null || req.recommendation() != null
                        ? EventType.EVALUATION
                        : EventType.STAGE_CHANGE,
                from, to, req.rating(), req.recommendation(), req.comment());
        audit.record(MODULE, ENTITY, applicationId.toString(),
                "TRANSITION", Map.of("from", from.name()),
                Map.of("to", to.name(),
                        "rating", req.rating() == null ? "" : req.rating().toString(),
                        "recommendation", req.recommendation() == null ? "" : req.recommendation().name(),
                        "comment", req.comment() == null ? "" : req.comment()));
        return saved;
    }

    private Application hire(Application a, StageTransitionRequest req) {
        Vacancy v = vacancies.findById(a.getVacancyId())
                .orElseThrow(() -> new BadRequestException("Vacancy missing"));
        Candidate c = candidates.findById(a.getCandidateId())
                .orElseThrow(() -> new BadRequestException("Candidate missing"));
        Offer offer = offers.findByApplicationId(a.getId()).orElse(null);

        LocalDate hireDate = offer != null && offer.getProposedStartDate() != null
                ? offer.getProposedStartDate()
                : LocalDate.now();

        // Create the Employee record (status defaults to ON_PROBATION inside EmployeeService).
        // M61: new fields (maritalStatus, nationality, employmentType, ftePercent)
        // all default to null/PERMANENT/100 — recruitment doesn't yet capture them.
        EmployeeRequest empReq = new EmployeeRequest(
                c.getFirstName(), c.getLastName(), c.getMiddleName(),
                null,                 // birthDate
                null,                 // gender
                null,                 // maritalStatus (M61)
                null,                 // nationality   (M61)
                null,                 // nationalId
                c.getEmail(),
                c.getPhone(),
                hireDate,
                v.getDepartment(),
                v.getTitle(),
                null,                 // costCentre
                null,                 // orgUnitId
                v.getPositionId(),
                null,                 // managerId
                null,                 // delegateManagerId (M37)
                null,                 // delegateFrom
                null,                 // delegateTo
                null,                 // employmentType (M61) — defaults to PERMANENT
                null,                 // ftePercent     (M61) — defaults to 100.00
                null,                 // leaveGroupId   (M66) — defaults to system default group
                null,                 // payrollGroupId (M75) — defaults to system default group
                null);                // matrixManagerId (M75)
        Employee created = employeeService.create(empReq);

        a.setCurrentStage(ApplicationStage.HIRED);
        a.setStatus(ApplicationStatus.HIRED);
        a.setCreatedEmployeeId(created.getId());
        Application saved = applications.save(a);

        // Increment the position's occupied headcount (PRD 8.10.8).
        if (v.getPositionId() != null) {
            try {
                staffingService.adjustOccupancy(v.getPositionId(), +1,
                        "Hired via " + a.getApplicationNo());
            } catch (Exception ex) {
                // Don't unwind the hire if the position bookkeeping fails — log as anomaly.
                audit.record(MODULE, ENTITY, a.getId().toString(),
                        "POSITION_UPDATE_FAILED", null,
                        Map.of("positionId", v.getPositionId().toString(),
                                "error", ex.getMessage()));
            }
        }

        // If all openings filled, close the vacancy.
        long hires = applications.countByVacancyIdAndStatus(v.getId(), ApplicationStatus.HIRED);
        if (hires >= v.getOpenings()) {
            v.setStatus(VacancyStatus.FILLED);
            vacancies.save(v);
        }

        recordEvent(a.getId(), EventType.STAGE_CHANGE,
                ApplicationStage.OFFER, ApplicationStage.HIRED,
                req.rating(), req.recommendation(),
                "Hired → Employee " + created.getEmployeeNo());

        audit.record(MODULE, ENTITY, a.getId().toString(),
                "HIRE", null,
                Map.of("employeeId", created.getId().toString(),
                        "employeeNo", created.getEmployeeNo(),
                        "hireDate", hireDate.toString()));
        return saved;
    }

    private void recordEvent(UUID applicationId, EventType type,
                              ApplicationStage from, ApplicationStage to,
                              Integer rating, az.millers.hcm.recruitment.domain.Recommendation rec,
                              String comment) {
        ApplicationEvent e = new ApplicationEvent();
        e.setApplicationId(applicationId);
        e.setEventType(type);
        e.setFromStage(from);
        e.setToStage(to);
        e.setRating(rating);
        e.setRecommendation(rec);
        e.setComment(comment);
        e.setActor(currentRequest.username());
        events.save(e);
    }
}
