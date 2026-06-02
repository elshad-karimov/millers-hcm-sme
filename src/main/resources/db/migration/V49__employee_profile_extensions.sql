-- M61 / Phase 1 Foundation
--
-- Additive columns on core_hr.employee surfacing PRD §8.1 gaps:
--   * marital_status       — required for AZ income tax deduction calc
--   * nationality          — ISO 3166-1 alpha-2 (work permit eligibility check)
--   * employment_type      — moved off staffing.position; needed when an employee
--                            has no linked position so the payroll engine can
--                            still resolve pro-rata. PERMANENT remains the default.
--   * fte_percent          — Full-Time Equivalent for pro-rata pay (PRD 8.1.1).
--                            100.00 = full-time; 50.00 = half-time. PayrollEngine
--                            multiplies baseSalary by fte/100 when type != PERMANENT.
--
-- All new columns are nullable / defaulted so existing rows remain valid.
-- No data backfill needed beyond the defaults Postgres applies on ADD COLUMN.

ALTER TABLE core_hr.employee
    ADD COLUMN IF NOT EXISTS marital_status   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS nationality      VARCHAR(2),
    ADD COLUMN IF NOT EXISTS employment_type  VARCHAR(20) NOT NULL DEFAULT 'PERMANENT',
    ADD COLUMN IF NOT EXISTS fte_percent      NUMERIC(5,2) NOT NULL DEFAULT 100.00;

-- Enum value guards. Using CHECK rather than a Postgres ENUM type so adding
-- a value later is a plain ALTER … ADD instead of a (much heavier) type swap.
ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS chk_employee_marital_status;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT chk_employee_marital_status
    CHECK (marital_status IS NULL OR marital_status IN (
        'SINGLE','MARRIED','DIVORCED','WIDOWED','CIVIL_PARTNERSHIP','OTHER'
    ));

ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS chk_employee_employment_type;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT chk_employee_employment_type
    CHECK (employment_type IN (
        'PERMANENT','FIXED_TERM','PART_TIME','PROBATIONARY','CONTRACTOR','INTERN'
    ));

ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS chk_employee_fte_percent;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT chk_employee_fte_percent
    CHECK (fte_percent > 0 AND fte_percent <= 100);

-- Nationality is a fixed 2-char ISO code when present.
ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS chk_employee_nationality_iso;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT chk_employee_nationality_iso
    CHECK (nationality IS NULL OR (length(nationality) = 2 AND nationality = upper(nationality)));

-- Helpful index for nationality-based filtering on the upcoming advanced
-- search (P1-15). Partial — most queries care only about non-null values.
CREATE INDEX IF NOT EXISTS idx_employee_nationality
    ON core_hr.employee (nationality)
    WHERE nationality IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_employee_employment_type
    ON core_hr.employee (employment_type);
