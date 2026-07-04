-- ----------------------------------------------------------------------------
-- M278 — Recruitment PRD §8: Job Posting Management.
--
-- A posting is a CHANNEL-SPECIFIC, LANGUAGE-SPECIFIC advertisement of
-- an approved requisition. One vacancy can have many postings (career
-- portal in AZ + EN, an internal posting, a job-board posting, …) —
-- previously the vacancy WAS the posting, which made multi-channel /
-- multi-language publishing impossible.
--
-- Channels: INTERNAL (employee career portal), EXTERNAL (public
-- careers page), JOB_BOARD, AGENCY, SOCIAL.
-- Languages: ISO 639-1 codes; AZ first-class per the PRD.
--
-- Lifecycle: DRAFT → PUBLISHED → PAUSED/EXPIRED/CLOSED.
-- ----------------------------------------------------------------------------

CREATE SEQUENCE IF NOT EXISTS recruitment.posting_no_seq START 1;

CREATE TABLE IF NOT EXISTS recruitment.job_posting (
    id                   UUID PRIMARY KEY,
    posting_no           VARCHAR(20)  NOT NULL UNIQUE,
    vacancy_id           UUID         NOT NULL REFERENCES recruitment.vacancy(id),
    channel              VARCHAR(20)  NOT NULL DEFAULT 'EXTERNAL',
    language             VARCHAR(5)   NOT NULL DEFAULT 'az',
    title                VARCHAR(200) NOT NULL,
    description          TEXT,
    requirements         TEXT,
    benefits_description TEXT,
    -- PRD §8 "Salary visibility control" — show the range publicly or not.
    salary_visible       BOOLEAN      NOT NULL DEFAULT FALSE,
    application_deadline DATE,
    status               VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_at         TIMESTAMPTZ,
    published_by         VARCHAR(120),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(120),
    updated_by           VARCHAR(120),
    CONSTRAINT chk_posting_channel CHECK (channel IN
        ('INTERNAL','EXTERNAL','JOB_BOARD','AGENCY','SOCIAL')),
    CONSTRAINT chk_posting_status CHECK (status IN
        ('DRAFT','PUBLISHED','PAUSED','EXPIRED','CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_posting_vacancy ON recruitment.job_posting(vacancy_id);
-- The public careers page + internal portal both query by
-- (channel, status) constantly.
CREATE INDEX IF NOT EXISTS idx_posting_channel_status
    ON recruitment.job_posting(channel, status);
