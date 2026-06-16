package az.millers.hcm.recruitment.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.EmployeeRequest;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.service.EmployeeService;
import az.millers.hcm.email.EmailService;
import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.service.ChecklistService;
import az.millers.hcm.preboarding.api.PreboardingDtos.IssueRequest;
import az.millers.hcm.preboarding.api.PreboardingDtos.IssueResponse;
import az.millers.hcm.preboarding.service.PreboardingInviteService;
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

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "Application";

    private final ApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final VacancyRepository vacancies;
    private final CandidateRepository candidates;
    private final OfferRepository offers;
    private final EmployeeService employeeService;
    private final ChecklistService checklistService;
    private final PreboardingInviteService preboardingInviteService;
    private final EmailService emailService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final String preboardingBaseUrl;
    private final int preboardingAutoExpiryDays;
    // M286 — pre-hire check gate (PRD §25): block hire on a FAILED check.
    private final PreHireCheckService preHireCheckService;
    // M287 — assessment gate (PRD §22): block hire on a FAILED assessment.
    private final AssessmentService assessmentService;
    // M295 — referral program (PRD Phase F): start the qualifying clock on hire.
    private final ReferralService referralService;
    // M296 — agency placement (PRD Phase F): close the ownership window + invoice on hire.
    private final AgencyService agencyService;

    public ApplicationService(ApplicationRepository applications,
                               ApplicationEventRepository events,
                               VacancyRepository vacancies,
                               CandidateRepository candidates,
                               OfferRepository offers,
                               EmployeeService employeeService,
                               ChecklistService checklistService,
                               PreboardingInviteService preboardingInviteService,
                               EmailService emailService,
                               AuditService audit,
                               CurrentRequest currentRequest,
                               PreHireCheckService preHireCheckService,
                               AssessmentService assessmentService,
                               ReferralService referralService,
                               AgencyService agencyService,
                               @Value("${hcm.preboarding.base-url}") String preboardingBaseUrl,
                               @Value("${hcm.preboarding.auto-invite-expiry-days:30}") int preboardingAutoExpiryDays) {
        this.applications = applications;
        this.events = events;
        this.vacancies = vacancies;
        this.candidates = candidates;
        this.offers = offers;
        this.employeeService = employeeService;
        this.checklistService = checklistService;
        this.preboardingInviteService = preboardingInviteService;
        this.emailService = emailService;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.preHireCheckService = preHireCheckService;
        this.assessmentService = assessmentService;
        this.referralService = referralService;
        this.agencyService = agencyService;
        this.preboardingBaseUrl = preboardingBaseUrl;
        this.preboardingAutoExpiryDays = preboardingAutoExpiryDays;
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
        // M274 — OPEN and PUBLISHED both accept candidates.
        if (!v.getStatus().isAccepting()) {
            throw new BadRequestException("Vacancy is not accepting applications: " + v.getStatus());
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

    /**
     * M289 — system auto-reject (PRD §15): close an application without a
     * human transition, used by the screening-knockout path. Idempotent —
     * an already-terminal application is left untouched. Records a
     * STAGE_CHANGE event + audit so the recruiter sees why.
     */
    @Transactional
    public void autoReject(UUID applicationId, String reason) {
        Application a = get(applicationId);
        if (a.getStatus() != ApplicationStatus.IN_PROGRESS) {
            return; // already HIRED / REJECTED / WITHDRAWN — nothing to do
        }
        ApplicationStage from = a.getCurrentStage();
        a.setCurrentStage(ApplicationStage.REJECTED);
        a.setStatus(ApplicationStatus.REJECTED);
        applications.save(a);
        recordEvent(applicationId, EventType.STAGE_CHANGE, from,
                ApplicationStage.REJECTED, null, null, reason);
        audit.record(MODULE, ENTITY, applicationId.toString(), "AUTO_REJECT",
                Map.of("from", from.name()),
                Map.of("reason", reason == null ? "" : reason));
        log.info("Application {} auto-rejected: {}", a.getApplicationNo(), reason);
    }

    private Application hire(Application a, StageTransitionRequest req) {
        Vacancy v = vacancies.findById(a.getVacancyId())
                .orElseThrow(() -> new BadRequestException("Vacancy missing"));
        Candidate c = candidates.findById(a.getCandidateId())
                .orElseThrow(() -> new BadRequestException("Candidate missing"));
        // M281 — PRD §10/§70: an internal candidate is ALREADY an employee;
        // hiring them here would create a duplicate employee record. The
        // correct path is a Position Transfer (M260) or Contract Change
        // POSITION (M270), which moves their existing record.
        if (c.getSource() == az.millers.hcm.recruitment.domain.CandidateSource.INTERNAL) {
            throw new BadRequestException(
                    "Internal candidate " + c.getCandidateNo() + " is an existing employee — "
                    + "use Position Transfer or Contract Change (POSITION) instead of hire, "
                    + "then mark this application REJECTED with reason 'internal move'");
        }
        // M286 — PRD §25: a FAILED blocks-hire pre-hire check stops the hire.
        preHireCheckService.assertNoBlockingFailures(a.getId());
        // M287 — PRD §22: a FAILED blocks-hire assessment stops the hire too.
        assessmentService.assertNoBlockingFailures(a.getId());
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
                null,                 // taxId — not captured at hire
                null,                 // socialInsuranceId — not captured at hire
                c.getEmail(),
                c.getPhone(),
                hireDate,
                v.getDepartment(),
                v.getTitle(),
                null,                 // costCentre
                null,                 // orgUnitId
                v.getPositionId(),
                null,                 // managerId
                null,                 // M141: workLocationId — not captured at hire
                null,                 // delegateManagerId (M37)
                null,                 // delegateFrom
                null,                 // delegateTo
                null,                 // employmentType (M61) — defaults to PERMANENT
                null,                 // ftePercent     (M61) — defaults to 100.00
                null,                 // leaveGroupId   (M66) — defaults to system default group
                null,                 // payrollGroupId (M75) — defaults to system default group
                null,                 // matrixManagerId (M75)
                null,                 // functionalManagerId (M146)
                null,                 // rehireEligible (M78) — null defaults to true
                // M132 — Section 1 cosmetic fields, not captured at hire.
                null, null, null, null, null,
                // M133 — Section 3 contact fields, not captured at hire.
                null, null, null, null, null,
                // M134 — Section 4 employment fields. Seniority date defaults
                // to hire_date downstream; category not captured at hire.
                null, null);
        Employee created = employeeService.create(empReq);

        a.setCurrentStage(ApplicationStage.HIRED);
        a.setStatus(ApplicationStatus.HIRED);
        a.setCreatedEmployeeId(created.getId());
        Application saved = applications.save(a);

        // M109 — position-headcount bump now lives inside
        // EmployeeService.create() so every hire path (recruitment, direct,
        // rehire, contract change) goes through exactly one bookkeeping
        // step. We deliberately don't bump here to avoid double-counting.

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

        // M159 + M298 — auto-start the best-matching ONBOARDING checklist for the
        // new hire (§8.10.6). The ChecklistTemplateMatcher picks the template whose
        // rules fit this hire most specifically (department / position / org unit /
        // location / employment type / category / nationality), falling back to a
        // rule-less default template. Non-fatal: if nothing matches the hire still
        // succeeds and HR can start a checklist manually.
        try {
            checklistService.startMatched(
                    created.getId(), ChecklistFlowType.ONBOARDING, hireDate, "Auto-started on hire")
                .ifPresentOrElse(
                    asg -> log.info("Onboarding checklist '{}' auto-started for employee {}",
                            asg.templateName(), created.getEmployeeNo()),
                    () -> log.info("No matching onboarding template for employee {} — none started",
                            created.getEmployeeNo()));
        } catch (Exception ex) {
            log.error("Failed to auto-start onboarding checklist for employee {}: {}",
                    created.getEmployeeNo(), ex.getMessage(), ex);
        }

        // M193 — auto-issue a pre-boarding invite and email the candidate so they
        // can fill in personal details before their first day.  Non-fatal: a failure
        // here must never block the hire transaction.
        try {
            IssueResponse invite = preboardingInviteService.issue(
                    new IssueRequest(created.getId(), preboardingAutoExpiryDays,
                            "Auto-issued on hire from recruitment (Application " + a.getApplicationNo() + ")"));
            String candidateEmail = c.getEmail();
            if (candidateEmail != null && !candidateEmail.isBlank()) {
                String link = preboardingBaseUrl + "?token=" + invite.plaintextToken();
                String html = "<p>Dear " + c.getFirstName() + ",</p>"
                        + "<p>Congratulations on your new role! Please complete your pre-boarding "
                        + "information before your start date using the link below.</p>"
                        + "<p><a href=\"" + link + "\">" + link + "</a></p>"
                        + "<p>This link expires in " + preboardingAutoExpiryDays + " days.</p>"
                        + "<p>Millers HCM</p>";
                emailService.sendSimple(candidateEmail,
                        "Welcome! Complete your pre-boarding details", html);
                log.info("Pre-boarding invite sent to {} for employee {}",
                        candidateEmail, created.getEmployeeNo());
            } else {
                log.info("Pre-boarding invite created for employee {} (no email — send link manually)",
                        created.getEmployeeNo());
            }
        } catch (Exception ex) {
            log.error("Failed to auto-issue pre-boarding invite for employee {}: {}",
                    created.getEmployeeNo(), ex.getMessage(), ex);
        }

        // M295 — if this candidate was referred, start the referral qualifying
        // clock. Non-fatal: never block the hire.
        try {
            referralService.markHiredForCandidate(c.getId(), hireDate);
        } catch (Exception ex) {
            log.error("Failed to mark referral hired for candidate {}: {}",
                    c.getId(), ex.getMessage(), ex);
        }

        // M296 — if this candidate is under an agency ownership window, close
        // the submission + raise a placement invoice. Non-fatal.
        try {
            agencyService.onCandidateHired(c.getId(), created.getId(),
                    offer == null ? null : offer.getProposedSalary());
        } catch (Exception ex) {
            log.error("Failed to process agency placement for candidate {}: {}",
                    c.getId(), ex.getMessage(), ex);
        }

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
