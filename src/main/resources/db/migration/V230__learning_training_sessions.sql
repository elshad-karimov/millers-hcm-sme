-- HCM_14 LMS — M405 (Phase B.2)
-- Classroom / instructor-led sessions (PRD 14 §7) + attendance (§11). A session
-- schedules a COURSE with an instructor and a room; employees enrol into the
-- session (capacity-checked) and attendance is marked per person. Completing a
-- session finalises open attendance (ENROLLED → NO_SHOW).

CREATE TABLE learning.training_session (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     varchar(64) NOT NULL DEFAULT 'default',
    course_id     uuid NOT NULL REFERENCES learning.course (id),
    instructor_id uuid REFERENCES learning.instructor (id),
    room_id       uuid REFERENCES learning.training_room (id),
    start_at      timestamptz NOT NULL,
    end_at        timestamptz NOT NULL,
    capacity      int,
    status        varchar(20) NOT NULL DEFAULT 'SCHEDULED',
    notes         varchar(500),
    created_by    varchar(80),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT training_session_window_check CHECK (end_at > start_at),
    CONSTRAINT training_session_status_check CHECK (status IN
        ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED'))
);
CREATE INDEX training_session_course_idx ON learning.training_session (tenant_id, course_id);
CREATE INDEX training_session_date_idx ON learning.training_session (tenant_id, start_at);

CREATE TABLE learning.training_attendance (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     varchar(64) NOT NULL DEFAULT 'default',
    session_id    uuid NOT NULL REFERENCES learning.training_session (id) ON DELETE CASCADE,
    employee_id   uuid NOT NULL,
    status        varchar(20) NOT NULL DEFAULT 'ENROLLED',
    checked_in_at timestamptz,
    note          varchar(500),
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT training_attendance_unique UNIQUE (session_id, employee_id),
    CONSTRAINT training_attendance_status_check CHECK (status IN
        ('ENROLLED','ATTENDED','LATE','NO_SHOW','CANCELLED'))
);
CREATE INDEX training_attendance_employee_idx
    ON learning.training_attendance (tenant_id, employee_id);

COMMENT ON TABLE learning.training_session IS
    'HCM_14 M405 — scheduled classroom/virtual session of a course (PRD 14 §7).';
COMMENT ON TABLE learning.training_attendance IS
    'HCM_14 M405 — per-employee session enrolment + attendance (PRD 14 §11).';
