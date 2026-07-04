-- M132 — Section 1 cosmetic field completion.
--
-- The Employee Management spec §1 listed five identity / personal fields
-- that the codebase never carried. They sat below Phase 1 / 2 priority
-- when M61–M80 ran, but show up on every printed badge and HR card —
-- closing the gap now is a single one-shot migration.
--
--  preferred_name  : nickname used in the directory / printable badge.
--  place_of_birth  : free-text city/country pair; HR cards display it.
--  blood_group     : O+/O-/A+/.../AB- (8 enum-equivalents) for emergency
--                    response. Optional; legally allowed in AZ.
--  religion        : optional; collection gated by legal review. Stored
--                    only when the deployment configuration says so.
--  native_language : ISO 639-1 two-letter code (en/az/ru/tr/…) — matches
--                    M77 letter-engine locale resolution.

ALTER TABLE core_hr.employee
    ADD COLUMN IF NOT EXISTS preferred_name   VARCHAR(120),
    ADD COLUMN IF NOT EXISTS place_of_birth   VARCHAR(160),
    ADD COLUMN IF NOT EXISTS blood_group      VARCHAR(8),
    ADD COLUMN IF NOT EXISTS religion         VARCHAR(60),
    ADD COLUMN IF NOT EXISTS native_language  VARCHAR(2);

-- Blood group whitelist — keeps the directory free from "A pos" / "A+ve"
-- style inconsistencies that break emergency-card lookups.
ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS chk_employee_blood_group;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT chk_employee_blood_group
    CHECK (blood_group IS NULL OR blood_group IN (
        'O+','O-','A+','A-','B+','B-','AB+','AB-'
    ));

-- ISO 639-1 alpha-2 — same convention as letter-engine locale.
ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS chk_employee_native_language;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT chk_employee_native_language
    CHECK (native_language IS NULL
           OR (length(native_language) = 2
               AND native_language = lower(native_language)));

-- Searchable by preferred name — common on team directory autocomplete.
CREATE INDEX IF NOT EXISTS idx_employee_preferred_name
    ON core_hr.employee (preferred_name)
    WHERE preferred_name IS NOT NULL;
