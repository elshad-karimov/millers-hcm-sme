-- ----------------------------------------------------------------------------
-- M289 — Recruitment PRD §15: knockout / pre-screening questions.
--
-- Each job posting can carry an ordered list of screening questions the
-- candidate answers at apply time. A question marked `knockout` whose
-- answer fails its rule auto-rejects the application; a non-knockout
-- question is captured for the recruiter but never blocks. Answers are
-- stored per application with the computed pass/fail + a text snapshot of
-- the question so the recruiter view survives later question edits.
--
-- Rule encoding per type:
--   BOOLEAN       -> expected_boolean (NULL = any answer passes)
--   SINGLE_CHOICE -> acceptable_options (newline-separated passing values)
--   NUMERIC       -> [min_value, max_value] inclusive (either bound NULL = open)
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS recruitment.screening_question (
    id                 UUID PRIMARY KEY,
    posting_id         UUID NOT NULL
                           REFERENCES recruitment.job_posting(id) ON DELETE CASCADE,
    ordinal            INT          NOT NULL DEFAULT 0,
    question_text      TEXT         NOT NULL,
    question_type      VARCHAR(16)  NOT NULL,   -- BOOLEAN | SINGLE_CHOICE | NUMERIC
    options            TEXT,                    -- choices for SINGLE_CHOICE (newline-separated)
    knockout           BOOLEAN      NOT NULL DEFAULT TRUE,
    required           BOOLEAN      NOT NULL DEFAULT TRUE,
    expected_boolean   BOOLEAN,                 -- BOOLEAN: the passing answer
    acceptable_options TEXT,                    -- SINGLE_CHOICE: passing values (newline-separated)
    min_value          NUMERIC(14,2),           -- NUMERIC: inclusive lower bound
    max_value          NUMERIC(14,2),           -- NUMERIC: inclusive upper bound
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         VARCHAR(120),
    updated_by         VARCHAR(120),
    CONSTRAINT chk_screening_question_type
        CHECK (question_type IN ('BOOLEAN', 'SINGLE_CHOICE', 'NUMERIC'))
);

CREATE INDEX IF NOT EXISTS idx_screening_question_posting
    ON recruitment.screening_question (posting_id, ordinal);

CREATE TABLE IF NOT EXISTS recruitment.application_answer (
    id                     UUID PRIMARY KEY,
    application_id         UUID NOT NULL
                               REFERENCES recruitment.application(id) ON DELETE CASCADE,
    question_id            UUID NOT NULL
                               REFERENCES recruitment.screening_question(id),
    question_text_snapshot TEXT NOT NULL,
    answer_text            TEXT,
    passed                 BOOLEAN     NOT NULL,
    knockout               BOOLEAN     NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_application_answer UNIQUE (application_id, question_id)
);

CREATE INDEX IF NOT EXISTS idx_application_answer_application
    ON recruitment.application_answer (application_id);
