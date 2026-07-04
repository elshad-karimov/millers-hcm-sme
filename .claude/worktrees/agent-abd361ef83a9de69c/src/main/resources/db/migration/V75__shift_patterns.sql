-- M111 — Rotating shift patterns + per-employee assignments.
--
-- A ShiftPattern is a cycle of N days (e.g. cycle=7 for a weekly rotation
-- or cycle=4 for "2-on-2-off"). Each day in the cycle maps to either a
-- Shift (working day) or NULL (rest day). A PatternAssignment pins one
-- pattern to an employee over a date window; the auto-roster generator
-- expands assignments into concrete RosterEntry rows.

CREATE TABLE attendance.shift_pattern (
    id              uuid PRIMARY KEY,
    code            varchar(40) NOT NULL UNIQUE,
    name            varchar(120) NOT NULL,
    description     text,
    cycle_days      int  NOT NULL,
    active          boolean NOT NULL DEFAULT true,
    created_by      varchar(80),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT shift_pattern_cycle_positive
        CHECK (cycle_days >= 1 AND cycle_days <= 365)
);

CREATE INDEX shift_pattern_active_idx ON attendance.shift_pattern(active);

-- One row per day in the cycle. shift_id NULL means "off day".
CREATE TABLE attendance.shift_pattern_day (
    id              uuid PRIMARY KEY,
    pattern_id      uuid NOT NULL REFERENCES attendance.shift_pattern(id) ON DELETE CASCADE,
    day_index       int  NOT NULL,
    shift_id        uuid REFERENCES attendance.shift(id),
    notes           text,
    CONSTRAINT shift_pattern_day_index_nonneg
        CHECK (day_index >= 0 AND day_index < 365)
);

CREATE UNIQUE INDEX shift_pattern_day_uk
    ON attendance.shift_pattern_day (pattern_id, day_index);

-- An employee may have at most one OPEN (end_date IS NULL) assignment per
-- pattern; multiple historical (end-dated) assignments are fine.
CREATE TABLE attendance.pattern_assignment (
    id              uuid PRIMARY KEY,
    employee_id     uuid NOT NULL,
    pattern_id      uuid NOT NULL REFERENCES attendance.shift_pattern(id),
    start_date      date NOT NULL,
    end_date        date,
    anchor_day_index int NOT NULL DEFAULT 0,
    notes           text,
    created_by      varchar(80),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      varchar(80),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pattern_assignment_date_window
        CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT pattern_assignment_anchor_nonneg
        CHECK (anchor_day_index >= 0)
);

CREATE INDEX pattern_assignment_employee_idx
    ON attendance.pattern_assignment (employee_id, start_date);
CREATE INDEX pattern_assignment_pattern_idx
    ON attendance.pattern_assignment (pattern_id);

-- At most one OPEN assignment per (employee, pattern). Closed (end_date set)
-- assignments aren't constrained — useful for re-assigning later.
CREATE UNIQUE INDEX pattern_assignment_open_uk
    ON attendance.pattern_assignment (employee_id, pattern_id)
    WHERE end_date IS NULL;

COMMENT ON TABLE attendance.shift_pattern IS
    'M111 — Rotating shift pattern (a cycle of N days).';
COMMENT ON TABLE attendance.shift_pattern_day IS
    'M111 — One day in a shift pattern; shift_id NULL = off day.';
COMMENT ON TABLE attendance.pattern_assignment IS
    'M111 — Per-employee pattern assignment used by the auto-roster generator.';
