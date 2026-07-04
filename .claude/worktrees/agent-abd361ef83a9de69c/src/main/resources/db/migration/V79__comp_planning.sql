-- M118 — Compensation planning workbench (Phase 1).
--
-- Two tables: comp_cycle holds the annual merit-review cycle (date window
-- + budget pool + status); comp_proposal holds the per-employee proposed
-- new salary inside that cycle, with the manager who proposed it and the
-- HR approver who finalised it.

CREATE TABLE compbenefits.comp_cycle (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            varchar(40)  NOT NULL UNIQUE,
    name            varchar(200) NOT NULL,
    description     text,
    /** DRAFT → OPEN → CLOSED. CANCELLED is the abandon path. */
    status          varchar(20)  NOT NULL DEFAULT 'DRAFT',
    opens_on        date         NOT NULL,
    closes_on       date         NOT NULL,
    /** Total budget for the cycle, expressed as monthly salary increase delta. */
    pool_total      numeric(14,2) NOT NULL DEFAULT 0,
    currency        varchar(10)  NOT NULL DEFAULT 'AZN',
    created_by      varchar(80),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT comp_cycle_status_check
        CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'CANCELLED')),
    CONSTRAINT comp_cycle_date_window
        CHECK (closes_on >= opens_on),
    CONSTRAINT comp_cycle_pool_nonneg
        CHECK (pool_total >= 0)
);

CREATE INDEX comp_cycle_status_idx ON compbenefits.comp_cycle(status);

CREATE TABLE compbenefits.comp_proposal (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    cycle_id            uuid         NOT NULL REFERENCES compbenefits.comp_cycle(id),
    employee_id         uuid         NOT NULL,
    current_salary      numeric(14,2) NOT NULL,
    proposed_salary     numeric(14,2) NOT NULL,
    currency            varchar(10)  NOT NULL DEFAULT 'AZN',
    /** Free-text justification supplied by the proposing manager. */
    rationale           text,
    /** DRAFT → SUBMITTED → APPROVED / REJECTED. */
    status              varchar(20)  NOT NULL DEFAULT 'DRAFT',
    proposed_by         varchar(80),
    proposed_at         timestamptz  NOT NULL DEFAULT now(),
    decided_by          varchar(80),
    decided_at          timestamptz,
    decision_note       text,
    /** Effective date the new salary takes effect, written into EmployeeCompensation. */
    effective_on        date,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT comp_proposal_status_check
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT comp_proposal_salaries_nonneg
        CHECK (current_salary >= 0 AND proposed_salary >= 0)
);

-- At most one proposal per (cycle, employee). HR can edit before commit.
CREATE UNIQUE INDEX comp_proposal_cycle_employee_uk
    ON compbenefits.comp_proposal (cycle_id, employee_id);

CREATE INDEX comp_proposal_status_idx ON compbenefits.comp_proposal (cycle_id, status);
CREATE INDEX comp_proposal_proposer_idx ON compbenefits.comp_proposal (proposed_by);

COMMENT ON TABLE compbenefits.comp_cycle IS
    'M118 — Annual merit-review cycle with a budget pool and date window.';
COMMENT ON TABLE compbenefits.comp_proposal IS
    'M118 — Per-employee proposed new salary inside a cycle. '
    'APPROVED rows feed EmployeeCompensation on the effective_on date.';
