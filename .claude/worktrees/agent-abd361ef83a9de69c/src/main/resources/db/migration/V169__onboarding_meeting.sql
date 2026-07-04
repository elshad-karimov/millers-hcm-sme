-- ─────────────────────────────────────────────────────────────────────────────
-- M306 — Onboarding Phase C.2: first-day meeting scheduling + calendar invites.
--
-- Pairs a MEETING_SCHEDULE checklist task with a concrete calendar event:
-- title, location, start time, duration, extra attendee e-mails. On schedule
-- / reschedule / cancel, an RFC 5545 (.ics) invite is e-mailed to the new
-- hire + any extra attendees via the shared IcsCalendarBuilder (M290 reuse).
--
-- One SCHEDULED meeting per task (UNIQUE on task_status_id).
-- calendar_uid is the stable VCALENDAR UID — bumping calendar_sequence on
-- reschedule/cancel keeps calendar clients in sync.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE SEQUENCE IF NOT EXISTS lifecycle.meeting_no_seq START 1;

CREATE TABLE IF NOT EXISTS lifecycle.onboarding_meeting (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_no        VARCHAR(20) NOT NULL
                                  DEFAULT 'MEE-' || LPAD(nextval('lifecycle.meeting_no_seq')::text, 5, '0'),
    employee_id       UUID        NOT NULL REFERENCES core_hr.employee(id),
    assignment_id     UUID        REFERENCES lifecycle.checklist_assignment(id),
    task_status_id    UUID        UNIQUE REFERENCES lifecycle.checklist_task_status(id),
    title             VARCHAR(300) NOT NULL,
    description       TEXT,
    location          VARCHAR(500),
    starts_at         TIMESTAMPTZ NOT NULL,
    duration_minutes  INT         NOT NULL DEFAULT 60,
    attendee_emails   TEXT,
    calendar_uid      VARCHAR(100) NOT NULL,
    calendar_sequence INT         NOT NULL DEFAULT 0,
    invite_sent_at    TIMESTAMPTZ,
    status            VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    cancelled_reason  TEXT,
    scheduled_by      VARCHAR(200),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_onboarding_meeting_employee ON lifecycle.onboarding_meeting(employee_id);
