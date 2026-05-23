-- Recruitment module (PRD Section 8.10).
-- Vacancies linked to staffing positions, candidate pipeline, stage events,
-- offer management. On HIRED the application creates an Employee record
-- and increments the position's occupied_headcount (PRD 8.10.8).

CREATE SEQUENCE recruitment.vacancy_no_seq     START WITH 1;
CREATE SEQUENCE recruitment.candidate_no_seq   START WITH 1;
CREATE SEQUENCE recruitment.application_no_seq START WITH 1;
CREATE SEQUENCE recruitment.offer_no_seq       START WITH 1;

CREATE TABLE recruitment.vacancy (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    vacancy_no        VARCHAR(32)  NOT NULL UNIQUE,
    title             VARCHAR(200) NOT NULL,
    position_id       UUID,                 -- soft ref into staffing.position
    department        VARCHAR(160),
    location          VARCHAR(160),
    openings          INTEGER      NOT NULL DEFAULT 1,
    description       TEXT,
    requirements      TEXT,
    salary_min        NUMERIC(12, 2),
    salary_max        NUMERIC(12, 2),
    currency          VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    hiring_manager_id UUID,
    recruiter_id      UUID,
    -- OPEN, ON_HOLD, CLOSED, FILLED, CANCELLED  (PRD 8.10.1)
    status            VARCHAR(32)  NOT NULL,
    opening_date      DATE,
    closing_date      DATE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(120),
    updated_by        VARCHAR(120),

    CONSTRAINT chk_vacancy_openings CHECK (openings > 0)
);

CREATE INDEX idx_vacancy_position ON recruitment.vacancy (position_id);
CREATE INDEX idx_vacancy_status   ON recruitment.vacancy (status);

CREATE TABLE recruitment.candidate (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_no      VARCHAR(32)  NOT NULL UNIQUE,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    middle_name       VARCHAR(100),
    email             VARCHAR(160),
    phone             VARCHAR(40),
    -- LINKEDIN, REFERRAL, WEBSITE, AGENCY, JOB_BOARD, INTERNAL, OTHER
    source            VARCHAR(32),
    cv_url            VARCHAR(500),
    experience_years  NUMERIC(5, 2),
    expected_salary   NUMERIC(12, 2),
    currency          VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    skills            TEXT,
    notes             TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(120),
    updated_by        VARCHAR(120)
);

CREATE INDEX idx_candidate_email ON recruitment.candidate (email);

CREATE TABLE recruitment.application (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_no       VARCHAR(32)  NOT NULL UNIQUE,
    vacancy_id           UUID         NOT NULL REFERENCES recruitment.vacancy (id),
    candidate_id         UUID         NOT NULL REFERENCES recruitment.candidate (id),
    -- CV_SCREENING, HR_INTERVIEW, TECHNICAL_INTERVIEW, FINAL_INTERVIEW, OFFER, HIRED, REJECTED, WITHDRAWN
    current_stage        VARCHAR(40)  NOT NULL,
    -- IN_PROGRESS, HIRED, REJECTED, WITHDRAWN
    status               VARCHAR(32)  NOT NULL,
    created_employee_id  UUID,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(120),

    UNIQUE (vacancy_id, candidate_id)
);

CREATE INDEX idx_app_vacancy ON recruitment.application (vacancy_id);
CREATE INDEX idx_app_status  ON recruitment.application (status);

CREATE TABLE recruitment.application_event (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID         NOT NULL REFERENCES recruitment.application (id) ON DELETE CASCADE,
    -- STAGE_CHANGE, EVALUATION, NOTE
    event_type      VARCHAR(32)  NOT NULL,
    from_stage      VARCHAR(40),
    to_stage        VARCHAR(40),
    rating          INTEGER,                -- 1..5
    -- STRONG_HIRE, HIRE, NO_HIRE, STRONG_NO_HIRE
    recommendation  VARCHAR(32),
    comment         TEXT,
    actor           VARCHAR(120) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_event_application ON recruitment.application_event (application_id);

CREATE TABLE recruitment.offer (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    offer_no            VARCHAR(32)  NOT NULL UNIQUE,
    application_id      UUID         NOT NULL UNIQUE REFERENCES recruitment.application (id) ON DELETE CASCADE,
    proposed_salary     NUMERIC(12, 2),
    currency            VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    proposed_start_date DATE,
    benefits            TEXT,
    -- DRAFT, SENT, ACCEPTED, REJECTED, EXPIRED, RESCINDED
    status              VARCHAR(32)  NOT NULL,
    sent_at             TIMESTAMPTZ,
    sent_by             VARCHAR(120),
    response_at         TIMESTAMPTZ,
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
