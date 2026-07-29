package az.millers.hcm.lifecycle.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

@Entity
@Table(schema = "lifecycle", name = "onboarding_meeting")
@Getter
@Setter
public class OnboardingMeeting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "meeting_no", insertable = false, updatable = false)
    private String meetingNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(name = "task_status_id")
    private UUID taskStatusId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "location")
    private String location;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    /** Comma-separated extra attendee e-mails (new hire is always added automatically). */
    @Column(name = "attendee_emails")
    private String attendeeEmails;

    /** Stable VCALENDAR UID — assigned once, never changed. */
    @Column(name = "calendar_uid", nullable = false)
    private String calendarUid;

    /** Bumped on every reschedule / cancel so clients accept the update. */
    @Column(name = "calendar_sequence", nullable = false)
    private int calendarSequence;

    @Column(name = "invite_sent_at")
    private OffsetDateTime inviteSentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MeetingStatus status;

    @Column(name = "cancelled_reason")
    private String cancelledReason;

    @Column(name = "scheduled_by")
    private String scheduledBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = MeetingStatus.SCHEDULED;
        if (calendarUid == null) calendarUid = UUID.randomUUID().toString();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }
}
