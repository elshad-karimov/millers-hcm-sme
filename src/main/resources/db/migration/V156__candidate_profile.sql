-- ----------------------------------------------------------------------------
-- M291 — Recruitment PRD §11/§12: structured candidate profile.
--
-- Replaces the free-text candidate.skills / candidate.experience_years with
-- queryable child rows — education history, work experience, and rated
-- skills — so HR can search and the future CV-parser (deferred) has a
-- target shape. The free-text columns stay for back-compat / quick capture.
--
-- All three cascade-delete with the candidate (and with the M293 merge /
-- M294 anonymisation flows later in Phase B).
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS recruitment.candidate_education (
    id              UUID PRIMARY KEY,
    candidate_id    UUID NOT NULL
                        REFERENCES recruitment.candidate(id) ON DELETE CASCADE,
    institution     VARCHAR(200) NOT NULL,
    degree          VARCHAR(120),
    field_of_study  VARCHAR(160),
    start_year      INT,
    end_year        INT,
    grade           VARCHAR(60),
    ordinal         INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_candidate_education_years
        CHECK (start_year IS NULL OR end_year IS NULL OR end_year >= start_year)
);

CREATE INDEX IF NOT EXISTS idx_candidate_education_candidate
    ON recruitment.candidate_education (candidate_id, ordinal);

CREATE TABLE IF NOT EXISTS recruitment.candidate_experience (
    id            UUID PRIMARY KEY,
    candidate_id  UUID NOT NULL
                      REFERENCES recruitment.candidate(id) ON DELETE CASCADE,
    company       VARCHAR(200) NOT NULL,
    title         VARCHAR(160) NOT NULL,
    location      VARCHAR(160),
    start_date    DATE,
    end_date      DATE,                       -- NULL while is_current
    is_current    BOOLEAN      NOT NULL DEFAULT FALSE,
    description   TEXT,
    ordinal       INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_candidate_experience_dates
        CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX IF NOT EXISTS idx_candidate_experience_candidate
    ON recruitment.candidate_experience (candidate_id, ordinal);

CREATE TABLE IF NOT EXISTS recruitment.candidate_skill (
    id               UUID PRIMARY KEY,
    candidate_id     UUID NOT NULL
                         REFERENCES recruitment.candidate(id) ON DELETE CASCADE,
    name             VARCHAR(100) NOT NULL,
    proficiency      VARCHAR(16),             -- BEGINNER | INTERMEDIATE | ADVANCED | EXPERT
    years_experience NUMERIC(4,1),
    ordinal          INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_candidate_skill UNIQUE (candidate_id, name),
    CONSTRAINT chk_candidate_skill_proficiency
        CHECK (proficiency IS NULL
               OR proficiency IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'))
);

CREATE INDEX IF NOT EXISTS idx_candidate_skill_candidate
    ON recruitment.candidate_skill (candidate_id, ordinal);
