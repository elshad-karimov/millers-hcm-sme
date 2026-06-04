-- M112 — Tag DailySummary rows with the source of their schedule decision.
--
-- Before M110 the only source was a WorkSchedule template. After M110/M111
-- a rostered Shift can drive a row instead; the new columns let downstream
-- consumers (payroll, exception reports) distinguish the two without
-- joining back to roster/schedule tables.

ALTER TABLE attendance.daily_summary
    ADD COLUMN shift_id uuid REFERENCES attendance.shift(id),
    ADD COLUMN source   varchar(20) NOT NULL DEFAULT 'SCHEDULE',
    ADD CONSTRAINT daily_summary_source_check
        CHECK (source IN ('SCHEDULE', 'ROSTER', 'NONE'));

CREATE INDEX daily_summary_source_idx ON attendance.daily_summary (source);

COMMENT ON COLUMN attendance.daily_summary.shift_id IS
    'M112 — When source=ROSTER, the rostered Shift that drove the computation.';
COMMENT ON COLUMN attendance.daily_summary.source IS
    'M112 — Where the scheduled window came from: SCHEDULE (legacy WorkSchedule), '
    'ROSTER (M110 RosterEntry), or NONE (no schedule + no roster on that date).';
