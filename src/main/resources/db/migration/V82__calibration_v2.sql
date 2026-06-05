-- M121 — Calibration board v2.
--
-- Three additions to the M56 calibration model:
--   1. Per-cycle target distribution — HR sets "10% 5s, 30% 4s, 40% 3s,
--      15% 2s, 5% 1s" before reviews start, and the board surfaces
--      actual-vs-target deltas during calibration.
--   2. Session lock — completing a calibration session freezes the
--      reviews participating in it, so a downstream re-open doesn't
--      silently overwrite a calibration outcome.
--   3. Edit log — every calibrateReview call inside an IN_PROGRESS
--      session writes a before/after row so HR can audit "who suggested
--      what" after the meeting.

-- 1. Target distribution per cycle.
CREATE TABLE performance.cycle_calibration_target (
    cycle_id        UUID         NOT NULL
                        REFERENCES performance.review_cycle(id) ON DELETE CASCADE,
    band            VARCHAR(64)  NOT NULL,
    target_percent  NUMERIC(5,2) NOT NULL,

    PRIMARY KEY (cycle_id, band),
    CONSTRAINT chk_target_percent_range
        CHECK (target_percent >= 0 AND target_percent <= 100)
);

-- 2. Session lock — record WHEN the session closed and HOW MANY reviews
--    were sealed by it. The per-review lock flag lives on performance_review
--    (next migration step in this file) so reads don't have to join twice.
ALTER TABLE performance.calibration_session
    ADD COLUMN locked_at      TIMESTAMPTZ,
    ADD COLUMN locked_reviews INTEGER NOT NULL DEFAULT 0;

ALTER TABLE performance.performance_review
    ADD COLUMN calibration_locked BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_perf_review_calibration_locked
    ON performance.performance_review (calibration_locked)
    WHERE calibration_locked = TRUE;

-- 3. Edit log — append-only row per calibrate() within an IN_PROGRESS
--    session. Stores the actor + the before/after JSON snapshots of the
--    review fields the operation touches (rating, band, recommendation,
--    bonus, notes). Joins back to calibration_session for "all edits in
--    this meeting" queries.
CREATE TABLE performance.calibration_edit_log (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id    UUID         NOT NULL
                      REFERENCES performance.calibration_session(id) ON DELETE CASCADE,
    review_id     UUID         NOT NULL
                      REFERENCES performance.performance_review(id) ON DELETE CASCADE,
    edited_by     VARCHAR(160) NOT NULL,
    edited_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    before_json   JSONB,
    after_json    JSONB
);

CREATE INDEX idx_calibration_edit_log_session
    ON performance.calibration_edit_log (session_id, edited_at DESC);

CREATE INDEX idx_calibration_edit_log_review
    ON performance.calibration_edit_log (review_id, edited_at DESC);
