-- M73 / Phase 2 — Probation review milestones (P2-01, HIGH)
--
-- Builds on M64's lifecycle.employment_contract.probation_end_date. The
-- contract carries the END date; this table tracks the FORMAL REVIEW
-- milestones (mid-point + final) leading up to it.
--
-- Lifecycle:
--   * Contract activated with probation_end_date set
--     → EmploymentContractService creates 2 review rows automatically:
--       MID_POINT (halfway through probation) + FINAL (5 days before end).
--   * Each review row is SCHEDULED; the M61 ExpiryAlertScheduler picks them
--     up via a new ProbationReviewExpirySource and fires alerts at
--     {30, 15, 7, 0} days ahead.
--   * On completion, manager + HR each record their feedback and the
--     review moves to COMPLETED with an outcome (PASSED / FAILED / EXTENDED).
--   * If probation ends early (e.g. via termination), outstanding reviews
--     are cancelled.

CREATE TABLE IF NOT EXISTS lifecycle.probation_review (
    id                      UUID         PRIMARY KEY,
    employee_id             UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    -- Soft FK to lifecycle.employment_contract — the row may be cancelled
    -- when the contract is, but we don't cascade hard (history preservation).
    contract_id             UUID         NOT NULL,
    review_type             VARCHAR(20)  NOT NULL,
    scheduled_date          DATE         NOT NULL,
    completed_date          DATE,

    -- Reviewers — both soft FKs to core_hr.employee. The manager is
    -- denormalised at scheduling time so re-org changes don't lose history.
    manager_employee_id     UUID,
    reviewer_employee_id    UUID,

    manager_feedback        TEXT,
    manager_rating          INTEGER,    -- 1..5 scale; NULL until manager completes
    hr_feedback             TEXT,
    hr_rating               INTEGER,    -- 1..5 scale; NULL until HR completes

    status                  VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    outcome                 VARCHAR(20),
    -- Effective date of the outcome (probation pass = confirmation date,
    -- extension = new probation_end_date).
    effective_date          DATE,
    notes                   TEXT,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(80),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by              VARCHAR(80),

    CONSTRAINT chk_pr_review_type CHECK (review_type IN ('MID_POINT','FINAL')),
    CONSTRAINT chk_pr_status      CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED')),
    CONSTRAINT chk_pr_outcome     CHECK (outcome IS NULL OR outcome IN ('PASSED','FAILED','EXTENDED')),
    CONSTRAINT chk_pr_completed   CHECK (
        -- COMPLETED rows must have outcome + completed_date
        (status <> 'COMPLETED') OR (outcome IS NOT NULL AND completed_date IS NOT NULL)
    ),
    CONSTRAINT chk_pr_manager_rating CHECK (manager_rating IS NULL OR (manager_rating BETWEEN 1 AND 5)),
    CONSTRAINT chk_pr_hr_rating      CHECK (hr_rating IS NULL OR (hr_rating BETWEEN 1 AND 5))
);

CREATE INDEX IF NOT EXISTS idx_pr_employee
    ON lifecycle.probation_review (employee_id, scheduled_date);

CREATE INDEX IF NOT EXISTS idx_pr_contract
    ON lifecycle.probation_review (contract_id);

-- Hot path for the ExpiryAlertScheduler — only SCHEDULED rows are alerted.
CREATE INDEX IF NOT EXISTS idx_pr_scheduled
    ON lifecycle.probation_review (scheduled_date)
    WHERE status = 'SCHEDULED';

-- At most one row per (contract, type) — prevents accidental duplicate
-- mid-point reviews when the auto-create job runs twice.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pr_contract_type
    ON lifecycle.probation_review (contract_id, review_type);
