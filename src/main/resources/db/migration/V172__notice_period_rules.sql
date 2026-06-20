-- M314 — Notice Period Management (Offboarding PRD §9).
-- Configurable rules: employment_type + probation flag → notice_days.
-- Null employment_type = catch-all default rule (lowest priority).
-- On resignation approval ResignationService reads this table to set
-- calculated_last_working_date = resignation_date + notice_days.

CREATE TABLE lifecycle.notice_period_rule (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employment_type VARCHAR(30),                         -- NULL = applies to any type (catch-all)
    on_probation    BOOLEAN      NOT NULL DEFAULT FALSE, -- when true only matches ON_PROBATION employees
    notice_days     INT          NOT NULL CHECK (notice_days >= 0),
    description     TEXT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_notice_rule UNIQUE NULLS NOT DISTINCT (employment_type, on_probation)
);

-- AZ Labour Code article 70: employee resignation notice = 1 month (30 calendar days).
-- Probation period: 14 calendar days (article 71).
-- CONTRACTOR / INTERN: typically shorter (14 days — policy default; no statutory rule).
INSERT INTO lifecycle.notice_period_rule (employment_type, on_probation, notice_days, description) VALUES
    (NULL,           TRUE,  14, 'AZ Labour Code art.71: probation-period resignation notice 14 days'),
    ('PERMANENT',    FALSE, 30, 'AZ Labour Code art.70: standard resignation notice 1 month'),
    ('FIXED_TERM',   FALSE, 30, 'AZ Labour Code art.70: fixed-term resignation notice 1 month'),
    ('PART_TIME',    FALSE, 30, 'AZ Labour Code art.70: part-time resignation notice 1 month'),
    ('PROBATIONARY', FALSE, 14, 'AZ Labour Code art.71: probationary employment notice 14 days'),
    ('CONTRACTOR',   FALSE, 14, 'Policy default: contractor notice 14 days'),
    ('INTERN',       FALSE,  7, 'Policy default: intern notice 7 days');
