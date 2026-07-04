-- M137 — Section 18 medical disability + vaccinations.
--
-- The audit found two §18 gaps the M65 EmployeeHealth row didn't cover.
-- Both are GDPR Article 9 special-category data — collection is
-- conditional on jurisdiction + employee consent, but when the system
-- IS storing them, structure matters more than free-text notes.
--
--  Disability columns
--    Land on the existing employee_health row (one-per-employee) so
--    the same role gate + AES-encrypted notes pattern already in place
--    extends naturally:
--      disability_status     : configurable token (NONE / DECLARED /
--                              REGISTERED / NOT_DISCLOSED). Free-form
--                              VARCHAR per the spec's wording ("if
--                              legally allowed and needed") so
--                              deployments can localise the taxonomy.
--      disability_percent    : statutory severity (0..100) used in AZ
--                              + other jurisdictions for accommodation
--                              + tax-benefit eligibility.
--      disability_note       : free-form, encrypted at rest via the
--                              same EncryptedStringConverter as
--                              occupational_health_notes.
--      accommodations_note   : workplace accommodation details. Same
--                              encryption + role gate.
--
--  employee_vaccination table
--    One row per dose so the system can store flu/COVID booster
--    history without overwriting prior records. Lot number matters
--    for safety recalls; next_dose_date drives the existing
--    ExpiryAlertScheduler (M61) once we wire ExpiryTrackable.

-- ── 1. Disability columns on employee_health ───────────────────────────────
ALTER TABLE core_hr.employee_health
    ADD COLUMN IF NOT EXISTS disability_status   VARCHAR(40),
    ADD COLUMN IF NOT EXISTS disability_percent  INTEGER,
    ADD COLUMN IF NOT EXISTS disability_note     VARCHAR(4000),
    ADD COLUMN IF NOT EXISTS accommodations_note VARCHAR(4000);

ALTER TABLE core_hr.employee_health
    DROP CONSTRAINT IF EXISTS chk_eh_disability_percent;
ALTER TABLE core_hr.employee_health
    ADD CONSTRAINT chk_eh_disability_percent
    CHECK (disability_percent IS NULL
        OR (disability_percent BETWEEN 0 AND 100));

-- ── 2. Vaccinations ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS core_hr.employee_vaccination (
    id                  UUID         PRIMARY KEY,
    employee_id         UUID         NOT NULL
                        REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    -- Stable code + human-readable name. Code lets reports cluster by
    -- vaccine kind without parsing the name string.
    vaccine_code        VARCHAR(60)  NOT NULL,
    vaccine_name        VARCHAR(200) NOT NULL,
    administered_date   DATE         NOT NULL,
    administered_by     VARCHAR(160),
    -- Lot tracking — required for any FDA / WHO safety recall response.
    lot_number          VARCHAR(60),
    -- When the next dose is due. Drives the upcoming ExpiryTrackable
    -- hook so HR + the employee get a reminder.
    next_dose_date      DATE,
    -- Optional certificate / proof-of-vaccination attachment URL.
    attachment_url      VARCHAR(500),
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(80),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          VARCHAR(80),

    -- Same employee can have many doses of the same vaccine over time
    -- (boosters, annual flu shot, …) — so we DON'T add a uniqueness
    -- constraint on (employee_id, vaccine_code).
    CONSTRAINT chk_ev_next_after_administered
        CHECK (next_dose_date IS NULL OR next_dose_date >= administered_date)
);

CREATE INDEX IF NOT EXISTS idx_emp_vacc_employee
    ON core_hr.employee_vaccination (employee_id, administered_date DESC);

-- Vaccines with an open next-dose date drive the scheduler reminders.
CREATE INDEX IF NOT EXISTS idx_emp_vacc_next_dose
    ON core_hr.employee_vaccination (next_dose_date)
    WHERE next_dose_date IS NOT NULL;
