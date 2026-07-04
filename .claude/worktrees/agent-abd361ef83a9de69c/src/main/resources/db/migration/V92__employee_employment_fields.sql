-- M134 — Section 4 employment field completion.
--
-- The Employee Management spec §4 listed two employment fields the
-- codebase didn't carry that are real consumers downstream:
--
--   employee_category  : configurable bucket distinct from
--                        employment_type. Real HCMs commonly use this
--                        for white/blue-collar, salaried/hourly,
--                        executive/manager/IC, or local/expat. Kept
--                        as a free-form VARCHAR per the spec wording
--                        ("configurable") instead of an enum, so each
--                        deployment can decide its own taxonomy.
--   seniority_date     : tenure anchor distinct from hire_date. Lets
--                        rehires + group-company transferees carry
--                        their original tenure for benefit accrual
--                        (e.g. annual-leave seniority brackets — see
--                        leave_mgmt.leave_type.seniority_brackets,
--                        which already counts years from hire_date and
--                        will switch to seniority_date when present).
--
-- Both nullable; existing rows unchanged.

ALTER TABLE core_hr.employee
    ADD COLUMN IF NOT EXISTS employee_category VARCHAR(60),
    ADD COLUMN IF NOT EXISTS seniority_date    DATE;

-- Seniority date can be earlier than hire_date for rehires/transferees
-- (carrying credit from a prior stint), but should never be in the
-- future — tenure can't start before it's recorded.
ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS chk_employee_seniority_date;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT chk_employee_seniority_date
    CHECK (seniority_date IS NULL OR seniority_date <= CURRENT_DATE);

-- Common filter: "list all white-collar staff" / "show executive headcount".
CREATE INDEX IF NOT EXISTS idx_employee_category
    ON core_hr.employee (employee_category)
    WHERE employee_category IS NOT NULL;

-- Tenure-based queries lean on seniority_date — keep it indexed.
CREATE INDEX IF NOT EXISTS idx_employee_seniority_date
    ON core_hr.employee (seniority_date)
    WHERE seniority_date IS NOT NULL;
