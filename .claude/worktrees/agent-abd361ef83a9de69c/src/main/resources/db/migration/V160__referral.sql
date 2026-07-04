-- ----------------------------------------------------------------------------
-- M295 — Recruitment PRD Phase F: employee referral program → payroll bonus.
--
-- An employee refers a candidate; when that candidate is hired the referral
-- moves to HIRED and a qualifying clock starts (qualifying_days, default 90).
-- Once qualified, HR pays the referral bonus into a payroll run (reusing the
-- M15 payroll_bonus mechanism), which flips it to PAID with a backref.
-- ----------------------------------------------------------------------------

CREATE SEQUENCE IF NOT EXISTS recruitment.referral_no_seq START 1;

CREATE TABLE IF NOT EXISTS recruitment.referral (
    id                    UUID PRIMARY KEY,
    referral_no           VARCHAR(20)  NOT NULL UNIQUE,
    referrer_employee_id  UUID         NOT NULL,                 -- soft FK to core_hr.employee
    candidate_id          UUID         NOT NULL UNIQUE
                              REFERENCES recruitment.candidate(id) ON DELETE CASCADE,
    vacancy_id            UUID,
    status                VARCHAR(16)  NOT NULL DEFAULT 'SUBMITTED',
    bonus_amount          NUMERIC(12,2) NOT NULL,
    currency              VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    qualifying_days       INT          NOT NULL DEFAULT 90,
    hired_at              TIMESTAMPTZ,
    qualified_at          TIMESTAMPTZ,                           -- hired_at + qualifying_days
    paid_at               TIMESTAMPTZ,
    payroll_run_id        UUID,
    payroll_bonus_id      UUID,
    notes                 VARCHAR(1000),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            VARCHAR(120),
    updated_by            VARCHAR(120),
    CONSTRAINT chk_referral_status
        CHECK (status IN ('SUBMITTED', 'HIRED', 'QUALIFIED', 'PAID', 'REJECTED')),
    CONSTRAINT chk_referral_bonus CHECK (bonus_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_referral_status ON recruitment.referral (status);
CREATE INDEX IF NOT EXISTS idx_referral_referrer ON recruitment.referral (referrer_employee_id);
