package az.millers.hcm.recruitment.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.calendar.IcsCalendarBuilder;
import az.millers.hcm.calendar.IcsCalendarBuilder.Attendee;
import az.millers.hcm.calendar.IcsCalendarBuilder.Method;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.email.EmailService;
import az.millers.hcm.recruitment.domain.Application;
import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.domain.Interview;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.repo.ApplicationRepository;
import az.millers.hcm.recruitment.repo.CandidateRepository;
import az.millers.hcm.recruitment.repo.InterviewRepository;
import az.millers.hcm.recruitment.repo.VacancyRepository;

/**
 * M290 — Recruitment PRD §20: emails an RFC 5545 (.ics) calendar invite
 * for an interview to the interviewer + candidate, reusing the M89
 * {@link EmailService} attachment path and the shared
 * {@link IcsCalendarBuilder}.
 *
 * <p>A {@link Method#REQUEST} carries the schedule (initial + reschedule,
 * with the interview's bumped {@code calendarSequence}); a
 * {@link Method#CANCEL} retracts it. Every send is best-effort — a mail
 * failure is logged and returns {@code false}, never breaking the
 * surrounding interview transaction. On success {@code inviteSentAt} is
 * stamped on the interview.
 *
 * <p>Live Google/Outlook/Teams/Zoom API integration is a documented later
 * seam — it needs per-tenant OAuth credentials this deployment doesn't hold.
 */
@Service
public class InterviewInviteService {

    private static final Logger log = LoggerFactory.getLogger(InterviewInviteService.class);
    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "Interview";
    private static final DateTimeFormatter HUMAN =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm 'UTC'");

    private final InterviewRepository interviews;
    private final ApplicationRepository applications;
    private final CandidateRepository candidates;
    private final VacancyRepository vacancies;
    private final EmployeeRepository employees;
    private final EmailService emailService;
    private final AuditService audit;
    private final boolean enabled;
    private final String organizerEmail;
    private final String organizerName;

    public InterviewInviteService(InterviewRepository interviews,
                                   ApplicationRepository applications,
                                   CandidateRepository candidates,
                                   VacancyRepository vacancies,
                                   EmployeeRepository employees,
                                   EmailService emailService,
                                   AuditService audit,
                                   @Value("${hcm.recruitment.interview-invite.enabled:true}") boolean enabled,
                                   @Value("${hcm.mail.from}") String organizerEmail,
                                   @Value("${hcm.mail.from-name}") String organizerName) {
        this.interviews = interviews;
        this.applications = applications;
        this.candidates = candidates;
        this.vacancies = vacancies;
        this.employees = employees;
        this.emailService = emailService;
        this.audit = audit;
        this.enabled = enabled;
        this.organizerEmail = organizerEmail;
        this.organizerName = organizerName;
    }

    /**
     * Build + email the invite (or cancellation) for an interview. Returns
     * {@code true} only when at least one recipient was emailed; stamps
     * {@code inviteSentAt} on success. Never throws.
     */
    @Transactional
    public boolean send(Interview iv, boolean cancel) {
        if (!enabled) {
            log.debug("Interview invites disabled — skipping {}", iv.getInterviewNo());
            return false;
        }
        try {
            Application app = applications.findById(iv.getApplicationId()).orElse(null);
            Candidate cand = app == null ? null
                    : candidates.findById(app.getCandidateId()).orElse(null);
            Vacancy vac = app == null ? null
                    : vacancies.findById(app.getVacancyId()).orElse(null);
            Employee interviewer = employees.findById(iv.getInterviewerEmployeeId()).orElse(null);

            List<Attendee> attendees = new ArrayList<>();
            List<String> recipients = new ArrayList<>();
            String interviewerEmail = interviewer == null ? null : interviewerEmail(interviewer);
            if (interviewerEmail != null) {
                String nm = fullName(interviewer.getFirstName(), interviewer.getLastName());
                attendees.add(new Attendee(interviewerEmail, nm));
                recipients.add(interviewerEmail);
            }
            if (cand != null && cand.getEmail() != null && !cand.getEmail().isBlank()) {
                String nm = fullName(cand.getFirstName(), cand.getLastName());
                attendees.add(new Attendee(cand.getEmail(), nm));
                recipients.add(cand.getEmail());
            }
            if (recipients.isEmpty()) {
                log.info("Interview {} has no e-mailable recipients — invite skipped",
                        iv.getInterviewNo());
                return false;
            }

            String jobTitle = vac == null ? "Interview" : vac.getTitle();
            String candName = cand == null ? "candidate"
                    : fullName(cand.getFirstName(), cand.getLastName());
            String summary = (cancel ? "Cancelled: " : "") + "Interview — " + jobTitle
                    + " (" + candName + ")";
            String when = iv.getScheduledAt()
                    .withOffsetSameInstant(ZoneOffset.UTC).format(HUMAN);
            String description = "Interview " + iv.getInterviewNo()
                    + " for " + jobTitle + "."
                    + "\nCandidate: " + candName
                    + "\nWhen: " + when
                    + " (" + iv.getDurationMinutes() + " min)"
                    + (iv.getLocation() == null || iv.getLocation().isBlank()
                            ? "" : "\nWhere: " + iv.getLocation());

            Method method = cancel ? Method.CANCEL : Method.REQUEST;
            String ics = IcsCalendarBuilder.build(
                    iv.getId().toString(), iv.getCalendarSequence(), method,
                    iv.getScheduledAt(), iv.getDurationMinutes(),
                    summary, description, iv.getLocation(),
                    organizerEmail, organizerName, attendees);

            String htmlBody = "<p>" + (cancel
                    ? "The following interview has been <b>cancelled</b>:"
                    : "You are invited to the following interview:") + "</p>"
                    + "<ul>"
                    + "<li><b>Role:</b> " + jobTitle + "</li>"
                    + "<li><b>Candidate:</b> " + candName + "</li>"
                    + "<li><b>When:</b> " + when + " (" + iv.getDurationMinutes() + " min)</li>"
                    + (iv.getLocation() == null || iv.getLocation().isBlank()
                            ? "" : "<li><b>Where:</b> " + iv.getLocation() + "</li>")
                    + "</ul>"
                    + "<p>A calendar invitation is attached.</p><p>Millers HCM</p>";

            emailService.sendReport(recipients, summary, htmlBody,
                    "invite.ics", ics.getBytes(StandardCharsets.UTF_8),
                    "text/calendar; method=" + method.name() + "; charset=UTF-8");

            iv.setInviteSentAt(OffsetDateTime.now());
            interviews.save(iv);
            audit.record(MODULE, ENTITY, iv.getId().toString(),
                    cancel ? "INVITE_CANCEL" : "INVITE_SENT", null,
                    Map.of("recipients", String.join(",", recipients),
                            "sequence", iv.getCalendarSequence()));
            log.info("Interview {} invite ({}) sent to {} recipient(s)",
                    iv.getInterviewNo(), method, recipients.size());
            return true;
        } catch (Exception ex) {
            log.warn("Interview {} invite failed: {}", iv.getInterviewNo(), ex.getMessage());
            return false;
        }
    }

    /** Work e-mail first (the calendar lives there), personal as fallback. */
    private static String interviewerEmail(Employee e) {
        if (e.getWorkEmail() != null && !e.getWorkEmail().isBlank()) return e.getWorkEmail();
        if (e.getEmail() != null && !e.getEmail().isBlank()) return e.getEmail();
        return null;
    }

    private static String fullName(String first, String last) {
        String n = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return n.isEmpty() ? "—" : n;
    }
}
