package az.millers.hcm.lifecycle.service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.calendar.IcsCalendarBuilder;
import az.millers.hcm.calendar.IcsCalendarBuilder.Attendee;
import az.millers.hcm.calendar.IcsCalendarBuilder.Method;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.email.EmailService;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.UpdateTaskRequest;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.MeetingResponse;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.ScheduleMeetingRequest;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatusValue;
import az.millers.hcm.lifecycle.domain.MeetingStatus;
import az.millers.hcm.lifecycle.domain.OnboardingMeeting;
import az.millers.hcm.lifecycle.repo.ChecklistTaskStatusRepository;
import az.millers.hcm.lifecycle.repo.OnboardingMeetingRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M306 — Onboarding Phase C.2: schedule first-day meetings and e-mail RFC 5545
 * (.ics) calendar invites to the new hire + extra attendees. Reuses the shared
 * {@link IcsCalendarBuilder} (M290) and M89 {@link EmailService}. A MEETING_SCHEDULE
 * task is marked IN_PROGRESS on schedule and DONE on cancellation (leave DONE to HR).
 */
@Service
public class OnboardingMeetingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingMeetingService.class);
    private static final String MODULE = "LIFECYCLE";
    private static final String ENTITY = "OnboardingMeeting";
    private static final DateTimeFormatter HUMAN =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm 'UTC'");

    private final OnboardingMeetingRepository meetings;
    private final ChecklistTaskStatusRepository taskStatuses;
    private final ChecklistService checklistService;
    private final EmployeeRepository employees;
    private final EmailService emailService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final boolean invitesEnabled;
    private final String organizerEmail;
    private final String organizerName;

    public OnboardingMeetingService(OnboardingMeetingRepository meetings,
                                     ChecklistTaskStatusRepository taskStatuses,
                                     ChecklistService checklistService,
                                     EmployeeRepository employees,
                                     EmailService emailService,
                                     AuditService audit,
                                     CurrentRequest currentRequest,
                                     @Value("${hcm.onboarding.meeting-invite.enabled:true}") boolean invitesEnabled,
                                     @Value("${hcm.mail.from}") String organizerEmail,
                                     @Value("${hcm.mail.from-name}") String organizerName) {
        this.meetings = meetings;
        this.taskStatuses = taskStatuses;
        this.checklistService = checklistService;
        this.employees = employees;
        this.emailService = emailService;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.invitesEnabled = invitesEnabled;
        this.organizerEmail = organizerEmail;
        this.organizerName = organizerName;
    }

    @Transactional
    public MeetingResponse schedule(ScheduleMeetingRequest req) {
        if (req.employeeId() == null) throw new BadRequestException("employeeId is required");
        if (req.startsAt() == null) throw new BadRequestException("startsAt is required");
        if (req.title() == null || req.title().isBlank()) throw new BadRequestException("title is required");

        Employee hire = employees.findById(req.employeeId())
                .orElseThrow(() -> new BadRequestException("Employee not found: " + req.employeeId()));

        UUID assignmentId = null;
        if (req.taskStatusId() != null) {
            var ts = taskStatuses.findById(req.taskStatusId())
                    .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + req.taskStatusId()));
            assignmentId = ts.getAssignmentId();
            // one SCHEDULED meeting per task
            meetings.findByTaskStatusId(req.taskStatusId())
                    .filter(m -> m.getStatus() == MeetingStatus.SCHEDULED)
                    .ifPresent(m -> { throw new BadRequestException(
                            "Task already has a scheduled meeting: " + m.getMeetingNo()); });
        }

        OnboardingMeeting m = new OnboardingMeeting();
        m.setEmployeeId(req.employeeId());
        m.setAssignmentId(assignmentId);
        m.setTaskStatusId(req.taskStatusId());
        m.setTitle(req.title().trim());
        m.setDescription(blankToNull(req.description()));
        m.setLocation(blankToNull(req.location()));
        m.setStartsAt(req.startsAt());
        m.setDurationMinutes(req.durationMinutes() > 0 ? req.durationMinutes() : 60);
        m.setAttendeeEmails(blankToNull(req.attendeeEmails()));
        m.setCalendarUid(UUID.randomUUID().toString());
        m.setCalendarSequence(0);
        m.setStatus(MeetingStatus.SCHEDULED);
        m.setScheduledBy(currentRequest.username());
        meetings.save(m);

        if (req.taskStatusId() != null) {
            checklistService.updateTask(req.taskStatusId(), new UpdateTaskRequest(
                    ChecklistTaskStatusValue.IN_PROGRESS, null, null,
                    "Meeting scheduled: " + m.getTitle()));
        }

        sendInvite(m, hire, Method.REQUEST);
        audit.record(MODULE, ENTITY, m.getId().toString(), "SCHEDULE", null,
                Map.of("employeeId", req.employeeId().toString(), "title", m.getTitle(),
                        "startsAt", req.startsAt().toString()));
        return toResponse(m);
    }

    @Transactional
    public MeetingResponse reschedule(UUID id, ScheduleMeetingRequest req) {
        OnboardingMeeting m = load(id);
        if (m.getStatus() == MeetingStatus.CANCELLED)
            throw new BadRequestException("Cannot reschedule a cancelled meeting");

        if (req.startsAt() != null) m.setStartsAt(req.startsAt());
        if (req.durationMinutes() > 0) m.setDurationMinutes(req.durationMinutes());
        if (req.title() != null && !req.title().isBlank()) m.setTitle(req.title().trim());
        m.setDescription(blankToNull(req.description()));
        m.setLocation(blankToNull(req.location()));
        m.setAttendeeEmails(blankToNull(req.attendeeEmails()));
        m.setCalendarSequence(m.getCalendarSequence() + 1);
        meetings.save(m);

        Employee hire = employees.findById(m.getEmployeeId()).orElse(null);
        sendInvite(m, hire, Method.REQUEST);
        audit.record(MODULE, ENTITY, id.toString(), "RESCHEDULE", null,
                Map.of("sequence", String.valueOf(m.getCalendarSequence())));
        return toResponse(m);
    }

    @Transactional
    public MeetingResponse cancel(UUID id, String reason) {
        OnboardingMeeting m = load(id);
        if (m.getStatus() == MeetingStatus.CANCELLED)
            throw new BadRequestException("Meeting is already cancelled");
        m.setStatus(MeetingStatus.CANCELLED);
        m.setCancelledReason(blankToNull(reason));
        m.setCalendarSequence(m.getCalendarSequence() + 1);
        meetings.save(m);

        Employee hire = employees.findById(m.getEmployeeId()).orElse(null);
        sendInvite(m, hire, Method.CANCEL);
        audit.record(MODULE, ENTITY, id.toString(), "CANCEL", null,
                Map.of("reason", reason == null ? "" : reason));
        return toResponse(m);
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> forEmployee(UUID employeeId) {
        return meetings.findByEmployeeIdOrderByStartsAtAsc(employeeId)
                .stream().map(this::toResponse).toList();
    }

    // ── invite send (best-effort; never throws) ───────────────────────────────

    private void sendInvite(OnboardingMeeting m, Employee hire, Method method) {
        if (!invitesEnabled) { log.debug("Onboarding meeting invites disabled"); return; }
        try {
            List<Attendee> attendees = new ArrayList<>();
            List<String> recipients = new ArrayList<>();

            String hireEmail = employeeEmail(hire);
            if (hireEmail != null) {
                attendees.add(new Attendee(hireEmail, hire != null ? fullName(hire) : null));
                recipients.add(hireEmail);
            }

            if (m.getAttendeeEmails() != null && !m.getAttendeeEmails().isBlank()) {
                Arrays.stream(m.getAttendeeEmails().split("[,;\\s]+"))
                        .map(String::trim).filter(e -> !e.isBlank())
                        .forEach(e -> { attendees.add(new Attendee(e, null)); recipients.add(e); });
            }

            if (recipients.isEmpty()) {
                log.info("Meeting {} has no e-mailable recipients — invite skipped", m.getId());
                return;
            }

            boolean cancel = method == Method.CANCEL;
            String when = m.getStartsAt().withOffsetSameInstant(ZoneOffset.UTC).format(HUMAN);
            String subject = (cancel ? "Cancelled: " : "") + m.getTitle();
            String desc = m.getTitle()
                    + "\nWhen: " + when + " (" + m.getDurationMinutes() + " min)"
                    + (m.getLocation() != null ? "\nWhere: " + m.getLocation() : "")
                    + (m.getDescription() != null ? "\n\n" + m.getDescription() : "");

            String ics = IcsCalendarBuilder.build(
                    m.getCalendarUid(), m.getCalendarSequence(), method,
                    m.getStartsAt(), m.getDurationMinutes(),
                    subject, desc, m.getLocation(),
                    organizerEmail, organizerName, attendees);

            String hireName = hire != null ? fullName(hire) : "New hire";
            String html = "<p>" + (cancel
                    ? "The following onboarding meeting has been <b>cancelled</b>:"
                    : "You are invited to the following onboarding meeting:") + "</p>"
                    + "<ul>"
                    + "<li><b>Meeting:</b> " + m.getTitle() + "</li>"
                    + "<li><b>New hire:</b> " + hireName + "</li>"
                    + "<li><b>When:</b> " + when + " (" + m.getDurationMinutes() + " min)</li>"
                    + (m.getLocation() != null ? "<li><b>Where:</b> " + m.getLocation() + "</li>" : "")
                    + (m.getDescription() != null ? "<li><b>Notes:</b> " + m.getDescription() + "</li>" : "")
                    + "</ul><p>A calendar invitation is attached.</p><p>Millers HCM</p>";

            emailService.sendReport(recipients, subject, html,
                    "invite.ics", ics.getBytes(StandardCharsets.UTF_8),
                    "text/calendar; method=" + method.name() + "; charset=UTF-8");

            m.setInviteSentAt(OffsetDateTime.now());
            meetings.save(m);
            log.info("Meeting {} invite ({}) sent to {} recipient(s)", m.getId(), method, recipients.size());
        } catch (Exception ex) {
            log.warn("Meeting {} invite failed: {}", m.getId(), ex.getMessage());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private OnboardingMeeting load(UUID id) {
        return meetings.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found: " + id));
    }

    private MeetingResponse toResponse(OnboardingMeeting m) {
        return new MeetingResponse(
                m.getId(), m.getMeetingNo(), m.getEmployeeId(), m.getTaskStatusId(),
                m.getTitle(), m.getDescription(), m.getLocation(),
                m.getStartsAt(), m.getDurationMinutes(), m.getAttendeeEmails(),
                m.getCalendarSequence(), m.getInviteSentAt(),
                m.getStatus() != null ? m.getStatus().name() : null,
                m.getCancelledReason(), m.getScheduledBy(), m.getCreatedAt());
    }

    private static String employeeEmail(Employee e) {
        if (e == null) return null;
        if (e.getWorkEmail() != null && !e.getWorkEmail().isBlank()) return e.getWorkEmail();
        if (e.getEmail() != null && !e.getEmail().isBlank()) return e.getEmail();
        return null;
    }

    private static String fullName(Employee e) {
        return ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                + (e.getLastName() == null ? "" : e.getLastName())).trim();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
