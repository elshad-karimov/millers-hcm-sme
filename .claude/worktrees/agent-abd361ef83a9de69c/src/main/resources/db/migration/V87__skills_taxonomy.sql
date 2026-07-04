-- M127 — skills taxonomy expansion.
--
-- M59 (V20) shipped the catalogue + employee_competency + course→competency
-- mapping. What was missing for a real skills programme:
--   1. competency hierarchy (parent_id) so HR can group e.g.
--      "JVM languages" → "Java", "Kotlin", "Scala" and report at the
--      parent level when needed.
--   2. per-position required competencies — today positions are just
--      headcount slots; this lets HR say "Senior Engineer needs Java
--      level 4 + Cloud level 3, Communication level 4 mandatory".
--   3. extended source enum: SELF (self-rated, pre-validation) and
--      VALIDATED (manager-approved). The existing MANUAL/COURSE/IMPORT
--      values stay.

-- ── 1. hierarchy ──────────────────────────────────────────────────────────
ALTER TABLE learning.competency
    ADD COLUMN parent_id UUID REFERENCES learning.competency (id) ON DELETE SET NULL;

CREATE INDEX ix_competency_parent ON learning.competency (parent_id);

-- ── 2. position required competencies ────────────────────────────────────
CREATE TABLE staffing.position_required_competency (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    position_id     UUID         NOT NULL
                        REFERENCES staffing.position (id) ON DELETE CASCADE,
    competency_id   UUID         NOT NULL
                        REFERENCES learning.competency (id) ON DELETE CASCADE,
    required_level  INTEGER      NOT NULL,
    mandatory       BOOLEAN      NOT NULL DEFAULT TRUE,
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(160),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      VARCHAR(160),

    CONSTRAINT chk_prc_level CHECK (required_level BETWEEN 1 AND 5),
    UNIQUE (position_id, competency_id)
);

CREATE INDEX ix_prc_position   ON staffing.position_required_competency (position_id);
CREATE INDEX ix_prc_competency ON staffing.position_required_competency (competency_id);

-- ── 3. extend the source enum ────────────────────────────────────────────
-- M59 stored source as a free-form VARCHAR(32). Phase 2 we'll add a
-- check constraint with the full whitelist; for now the service-layer
-- enum is the source of truth and the column stays open.
