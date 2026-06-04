-- M110 — Shift catalog + roster.
--
-- Shift defines a single shift (Morning 08:00-16:00, Night 22:00-06:00, etc.).
-- RosterEntry pins one shift to one (employee, date). At most one shift per
-- employee per date is enforced by the unique constraint.

CREATE TABLE attendance.shift (
    id              uuid PRIMARY KEY,
    code            varchar(40) NOT NULL UNIQUE,
    name            varchar(120) NOT NULL,
    description     text,
    start_time      time NOT NULL,
    end_time        time NOT NULL,
    break_minutes   int  NOT NULL DEFAULT 0,
    crosses_midnight boolean NOT NULL DEFAULT false,
    color           varchar(7),  -- '#1677ff' style hex; nullable
    active          boolean NOT NULL DEFAULT true,
    created_by      varchar(80),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT shift_break_nonneg
        CHECK (break_minutes >= 0 AND break_minutes < 24 * 60),
    CONSTRAINT shift_color_format
        CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE INDEX shift_active_idx ON attendance.shift(active);

CREATE TABLE attendance.roster_entry (
    id              uuid PRIMARY KEY,
    employee_id     uuid NOT NULL,
    shift_id        uuid NOT NULL REFERENCES attendance.shift(id),
    roster_date     date NOT NULL,
    notes           text,
    locked          boolean NOT NULL DEFAULT false,
    created_by      varchar(80),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      varchar(80),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

-- At most one shift assignment per employee per date. (Multi-shift days are
-- represented by a single Shift definition spanning the combined window, not
-- by two roster rows — keeps payroll and attendance reconciliation simple.)
CREATE UNIQUE INDEX roster_entry_emp_date_uk
    ON attendance.roster_entry (employee_id, roster_date);

CREATE INDEX roster_entry_date_idx ON attendance.roster_entry (roster_date);
CREATE INDEX roster_entry_shift_idx ON attendance.roster_entry (shift_id);

COMMENT ON TABLE attendance.shift IS
    'M110 — Shift catalog (Morning, Night, Split, etc.).';
COMMENT ON TABLE attendance.roster_entry IS
    'M110 — Per-employee per-day shift assignment.';

-- Seed three canonical shifts so the demo + tests have something to render.
INSERT INTO attendance.shift (id, code, name, start_time, end_time, break_minutes,
                                crosses_midnight, color, active, created_by)
VALUES
    ('11111111-1111-1111-1111-111111110001', 'MORNING',  'Morning',
     '08:00', '16:00', 60, false, '#1677ff', true, 'system'),
    ('11111111-1111-1111-1111-111111110002', 'EVENING',  'Evening',
     '14:00', '22:00', 60, false, '#fa8c16', true, 'system'),
    ('11111111-1111-1111-1111-111111110003', 'NIGHT',    'Night',
     '22:00', '06:00', 60, true,  '#722ed1', true, 'system');
