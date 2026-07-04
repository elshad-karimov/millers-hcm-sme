-- ----------------------------------------------------------------------------
-- M296 — Recruitment PRD Phase F: recruitment agency master, candidate
-- ownership window, and placement invoicing.
--
-- An agency submits a candidate and gains an ownership window
-- (ownership_days). While that window is ACTIVE no other agency may submit
-- the same candidate. If the owned candidate is hired inside the window the
-- submission flips to HIRED and a DRAFT placement invoice is raised for the
-- agency fee (% of annualised salary, or a fixed amount).
-- ----------------------------------------------------------------------------

CREATE SEQUENCE IF NOT EXISTS recruitment.agency_no_seq START 1;
CREATE SEQUENCE IF NOT EXISTS recruitment.agency_submission_no_seq START 1;
CREATE SEQUENCE IF NOT EXISTS recruitment.agency_invoice_no_seq START 1;

CREATE TABLE IF NOT EXISTS recruitment.agency (
    id             UUID PRIMARY KEY,
    agency_no      VARCHAR(20)  NOT NULL UNIQUE,
    name           VARCHAR(200) NOT NULL,
    contact_name   VARCHAR(160),
    contact_email  VARCHAR(160),
    contact_phone  VARCHAR(40),
    fee_type       VARCHAR(16)  NOT NULL DEFAULT 'PERCENT_SALARY',  -- PERCENT_SALARY | FIXED
    fee_percent    NUMERIC(5,2),                                    -- for PERCENT_SALARY
    fee_fixed      NUMERIC(12,2),                                   -- for FIXED
    ownership_days INT          NOT NULL DEFAULT 180,
    currency       VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    notes          VARCHAR(1000),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(120),
    updated_by     VARCHAR(120),
    CONSTRAINT chk_agency_fee_type CHECK (fee_type IN ('PERCENT_SALARY', 'FIXED'))
);

CREATE TABLE IF NOT EXISTS recruitment.agency_submission (
    id              UUID PRIMARY KEY,
    submission_no   VARCHAR(20)  NOT NULL UNIQUE,
    agency_id       UUID         NOT NULL REFERENCES recruitment.agency(id),
    candidate_id    UUID         NOT NULL
                        REFERENCES recruitment.candidate(id) ON DELETE CASCADE,
    vacancy_id      UUID,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',         -- ACTIVE | EXPIRED | HIRED | WITHDRAWN
    submitted_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ownership_until TIMESTAMPTZ  NOT NULL,
    hire_employee_id UUID,
    notes           VARCHAR(1000),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(120),
    updated_by      VARCHAR(120),
    CONSTRAINT chk_agency_submission_status
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'HIRED', 'WITHDRAWN'))
);

CREATE INDEX IF NOT EXISTS idx_agency_submission_candidate
    ON recruitment.agency_submission (candidate_id);
CREATE INDEX IF NOT EXISTS idx_agency_submission_status
    ON recruitment.agency_submission (status);

CREATE TABLE IF NOT EXISTS recruitment.agency_invoice (
    id               UUID PRIMARY KEY,
    invoice_no       VARCHAR(20)  NOT NULL UNIQUE,
    agency_id        UUID         NOT NULL REFERENCES recruitment.agency(id),
    submission_id    UUID         NOT NULL REFERENCES recruitment.agency_submission(id),
    candidate_id     UUID         NOT NULL,
    hire_employee_id UUID,
    fee_amount       NUMERIC(12,2) NOT NULL,
    currency         VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',         -- DRAFT | SENT | PAID | CANCELLED
    issued_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ,
    paid_at          TIMESTAMPTZ,
    notes            VARCHAR(1000),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       VARCHAR(120),
    updated_by       VARCHAR(120),
    CONSTRAINT chk_agency_invoice_status
        CHECK (status IN ('DRAFT', 'SENT', 'PAID', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_agency_invoice_agency
    ON recruitment.agency_invoice (agency_id);
