-- M135 — dependents eligibility end date.
--
-- The Employee Management spec §13 listed "effective date / end date"
-- on dependents — needed when a child ages out of insurance, a spouse
-- divorces off the policy, a parent passes away, etc. The V57 table
-- only carried `is_active` (a manual flag) and `created_at`, so HR had
-- nowhere to record WHEN eligibility ended or WHY.
--
-- Two columns:
--   eligibility_end_date   : the day eligibility ended. Nullable —
--                            most rows leave it blank. When set and
--                            ≤ CURRENT_DATE, downstream eligibility
--                            queries treat the dependent as ineligible.
--   eligibility_end_reason : code from the whitelist below. Free-form
--                            details still live in the existing `notes`
--                            column.
--
-- Both nullable; existing rows unchanged.

ALTER TABLE core_hr.employee_dependent
    ADD COLUMN IF NOT EXISTS eligibility_end_date   DATE,
    ADD COLUMN IF NOT EXISTS eligibility_end_reason VARCHAR(60);

-- Reason whitelist — enough HCM categories to make benefit-eligibility
-- reporting useful without forcing HR to free-type a code.
ALTER TABLE core_hr.employee_dependent
    DROP CONSTRAINT IF EXISTS chk_dep_eligibility_end_reason;
ALTER TABLE core_hr.employee_dependent
    ADD CONSTRAINT chk_dep_eligibility_end_reason
    CHECK (eligibility_end_reason IS NULL OR eligibility_end_reason IN (
        'AGED_OUT',           -- child past the dependent-age cap
        'DIVORCE',            -- spouse separation
        'DEATH',              -- bereavement
        'MARRIED',            -- child gained own coverage
        'EMPLOYED',           -- child got their own job + benefits
        'STUDENT_STATUS_LOST',-- e.g. full-time student status ended
        'OTHER'
    ));

-- Reasons require a date — and a date requires a reason. Locks the
-- two columns into a single transaction so HR can't leave a
-- "dependent ended on … (reason: ?)" row in the DB.
ALTER TABLE core_hr.employee_dependent
    DROP CONSTRAINT IF EXISTS chk_dep_eligibility_end_pair;
ALTER TABLE core_hr.employee_dependent
    ADD CONSTRAINT chk_dep_eligibility_end_pair
    CHECK ((eligibility_end_date IS NULL AND eligibility_end_reason IS NULL)
        OR (eligibility_end_date IS NOT NULL AND eligibility_end_reason IS NOT NULL));

-- Index used by the upcoming "currently-eligible dependents" view used
-- by payroll family-allowance + benefits enrolment lookups.
CREATE INDEX IF NOT EXISTS idx_dep_eligibility_end
    ON core_hr.employee_dependent (employee_id, eligibility_end_date)
    WHERE eligibility_end_date IS NOT NULL;
