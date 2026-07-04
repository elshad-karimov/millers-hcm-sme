-- M87 — Talent pool / candidate CRM.
--
-- Three additions:
--   1. candidate gains pool_status + last_contacted_at so the pool view can
--      filter by activity / archived state without touching the existing
--      application-pipeline columns.
--   2. candidate_tag — many-to-one tags. Tags are free-text strings on
--      purpose so recruiters can introduce new ones without admin overhead.
--   3. candidate_note — separate from application-level notes; these are
--      cross-vacancy CRM touches (call recap, email outreach, conference).

ALTER TABLE recruitment.candidate
    ADD COLUMN IF NOT EXISTS pool_status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS last_contacted_at  TIMESTAMPTZ;

ALTER TABLE recruitment.candidate
    DROP CONSTRAINT IF EXISTS chk_candidate_pool_status;
ALTER TABLE recruitment.candidate
    ADD CONSTRAINT chk_candidate_pool_status
    CHECK (pool_status IN ('ACTIVE','PASSIVE','ARCHIVED','DO_NOT_CONTACT'));

CREATE INDEX IF NOT EXISTS idx_candidate_pool_status
    ON recruitment.candidate (pool_status);

CREATE INDEX IF NOT EXISTS idx_candidate_last_contacted
    ON recruitment.candidate (last_contacted_at DESC NULLS LAST);


-- ── candidate_tag ───────────────────────────────────────────────────────────
-- Free-text label attached to a candidate. Many tags per candidate; a tag
-- text can repeat across candidates so the same "React" tag appears in
-- many rows. UNIQUE on (candidate_id, lower(tag)) prevents accidental
-- duplicates from the same recruiter typing it twice.

CREATE TABLE IF NOT EXISTS recruitment.candidate_tag (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id    UUID         NOT NULL REFERENCES recruitment.candidate (id) ON DELETE CASCADE,
    tag             VARCHAR(60)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(80)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_candidate_tag_unique
    ON recruitment.candidate_tag (candidate_id, lower(tag));

CREATE INDEX IF NOT EXISTS idx_candidate_tag_label
    ON recruitment.candidate_tag (lower(tag));


-- ── candidate_note ──────────────────────────────────────────────────────────
-- CRM contact log. Append-only — recruiters add new notes; old ones aren't
-- edited (use a new note to clarify). kind hints what produced it.

CREATE TABLE IF NOT EXISTS recruitment.candidate_note (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id    UUID         NOT NULL REFERENCES recruitment.candidate (id) ON DELETE CASCADE,
    kind            VARCHAR(20)  NOT NULL DEFAULT 'NOTE',
    body            TEXT         NOT NULL,
    contact_date    DATE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(80),
    CONSTRAINT chk_candidate_note_kind CHECK (kind IN (
        'NOTE','CALL','EMAIL','MEETING','EVENT','REFERRAL','OTHER'
    ))
);

CREATE INDEX IF NOT EXISTS idx_candidate_note_candidate
    ON recruitment.candidate_note (candidate_id, created_at DESC);
