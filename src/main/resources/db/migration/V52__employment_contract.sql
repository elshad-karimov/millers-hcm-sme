-- M64 / Phase 1 — Employment contracts (P1-03, CRITICAL)
--
-- The gap audit's highest-priority item: until now there was no entity
-- tracking the contract a person signs on hire. Probation tracking, fixed-term
-- compliance, contract-expiry alerting, and renewal workflows were all blocked
-- on this table.
--
-- One contract per active employment period. Renewals close the prior
-- contract (status=RENEWED, end_date=renewal effective date - 1) and create a
-- new one. Implements the M61 ExpiryTrackable pattern for end_date and
-- (separately) probation_end_date.

-- Sequence for human-readable contract numbers (CT-00001, CT-00002, ...).
CREATE SEQUENCE IF NOT EXISTS lifecycle.contract_no_seq START 1;

CREATE TABLE IF NOT EXISTS lifecycle.employment_contract (
    id                      UUID         PRIMARY KEY,
    contract_no             VARCHAR(20)  NOT NULL UNIQUE,
    employee_id             UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,

    contract_type           VARCHAR(20)  NOT NULL,
    -- Matches employee.employment_type so payroll can resolve type from the
    -- active contract instead of the snapshot on the employee row when both
    -- exist. Same CHECK enum value set.
    start_date              DATE         NOT NULL,
    end_date                DATE,                  -- null = open-ended
    probation_end_date      DATE,                  -- null = no probation period
    notice_period_days      INTEGER      NOT NULL DEFAULT 30,

    status                  VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    signed_by_employee_at   TIMESTAMPTZ,
    signed_by_hr_at         TIMESTAMPTZ,

    -- Optional clauses. Template-based generation is future work (M70 / Phase 2).
    has_confidentiality     BOOLEAN      NOT NULL DEFAULT FALSE,
    non_compete_end_date    DATE,

    notes                   TEXT,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(80),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by              VARCHAR(80),

    CONSTRAINT chk_contract_type CHECK (contract_type IN (
        'PERMANENT','FIXED_TERM','PART_TIME','PROBATIONARY','CONTRACTOR','INTERN'
    )),
    CONSTRAINT chk_contract_status CHECK (status IN (
        'DRAFT','ACTIVE','EXPIRED','RENEWED','TERMINATED'
    )),
    CONSTRAINT chk_contract_dates CHECK (
        end_date IS NULL OR end_date >= start_date
    ),
    CONSTRAINT chk_contract_probation_dates CHECK (
        probation_end_date IS NULL OR probation_end_date >= start_date
    ),
    CONSTRAINT chk_contract_notice_period CHECK (notice_period_days >= 0)
);

CREATE INDEX IF NOT EXISTS idx_contract_employee
    ON lifecycle.employment_contract (employee_id, start_date DESC);

CREATE INDEX IF NOT EXISTS idx_contract_status
    ON lifecycle.employment_contract (status);

-- Expiry-scan hot paths — one index per date column the scheduler queries.
CREATE INDEX IF NOT EXISTS idx_contract_end_date
    ON lifecycle.employment_contract (end_date)
    WHERE end_date IS NOT NULL AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_contract_probation_end
    ON lifecycle.employment_contract (probation_end_date)
    WHERE probation_end_date IS NOT NULL AND status = 'ACTIVE';

-- Invariant: an employee may have at most one ACTIVE contract at a time.
-- Renewals must close (status=RENEWED) the prior one in the same TX before
-- the new one is inserted.
CREATE UNIQUE INDEX IF NOT EXISTS uq_contract_one_active_per_employee
    ON lifecycle.employment_contract (employee_id)
    WHERE status = 'ACTIVE';
