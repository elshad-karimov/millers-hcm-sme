-- M136 — skill years-of-experience + manager endorsement.
--
-- The Employee Management spec §16 listed two gaps the audit found:
--
--   years_of_experience   : tenure on this competency in years. Stored
--                           as NUMERIC(4,1) so "8.5 years" survives —
--                           common HR convention. Capped at 99.9 by
--                           precision.
--
--   manager endorsement   : authoritative confirmation that a stated
--                           proficiency level is real. Until now the
--                           only signal was the SOURCE column
--                           (MANUAL / COURSE / IMPORT) — none of which
--                           captures "a manager looked + agreed". Four
--                           paired columns below let a single endorsement
--                           live alongside the record; a future Phase 2
--                           can promote this to its own table if the
--                           use case grows multiple-endorsements-per-row.

ALTER TABLE learning.employee_competency
    ADD COLUMN IF NOT EXISTS years_of_experience   NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS endorsed_by_employee_id UUID
        REFERENCES core_hr.employee (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS endorsed_at           TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS endorsed_level        INTEGER,
    ADD COLUMN IF NOT EXISTS endorsement_note      TEXT;

-- Years of experience can't be negative. The numeric precision already
-- caps it at 99.9.
ALTER TABLE learning.employee_competency
    DROP CONSTRAINT IF EXISTS chk_ec_years_nonneg;
ALTER TABLE learning.employee_competency
    ADD CONSTRAINT chk_ec_years_nonneg
    CHECK (years_of_experience IS NULL OR years_of_experience >= 0);

-- Endorsed level mirrors proficiency's 1..5 scale.
ALTER TABLE learning.employee_competency
    DROP CONSTRAINT IF EXISTS chk_ec_endorsed_level;
ALTER TABLE learning.employee_competency
    ADD CONSTRAINT chk_ec_endorsed_level
    CHECK (endorsed_level IS NULL OR endorsed_level BETWEEN 1 AND 5);

-- Pair-or-neither on the three primary endorsement columns. The note is
-- optional even when endorsed — HR doesn't always have something to say.
ALTER TABLE learning.employee_competency
    DROP CONSTRAINT IF EXISTS chk_ec_endorsement_paired;
ALTER TABLE learning.employee_competency
    ADD CONSTRAINT chk_ec_endorsement_paired
    CHECK ((endorsed_by_employee_id IS NULL
            AND endorsed_at IS NULL
            AND endorsed_level IS NULL)
        OR (endorsed_by_employee_id IS NOT NULL
            AND endorsed_at IS NOT NULL
            AND endorsed_level IS NOT NULL));

-- "Show me everything I've endorsed" + dashboard counters lean on this.
CREATE INDEX IF NOT EXISTS idx_ec_endorsed_by
    ON learning.employee_competency (endorsed_by_employee_id, endorsed_at)
    WHERE endorsed_by_employee_id IS NOT NULL;
