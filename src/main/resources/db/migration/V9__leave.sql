-- Leave Management module (PRD Section 8.5).
-- This iteration ships leave types, balances per (employee, type, year),
-- requests routed through the Workflow Engine. Monthly accrual and
-- seniority-based entitlement are explicit later-work seams.

CREATE SEQUENCE leave_mgmt.leave_request_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE leave_mgmt.leave_type (
    id                              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code                            VARCHAR(64)  NOT NULL UNIQUE,
    name                            VARCHAR(200) NOT NULL,
    description                     TEXT,

    paid                            BOOLEAN      NOT NULL DEFAULT TRUE,
    requires_attachment             BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_replacement            BOOLEAN      NOT NULL DEFAULT FALSE,

    -- 0 means "no entitlement bank" (e.g. unpaid leave is approved without
    -- consuming a balance). NULL means "compute later" — used by seniority
    -- accrual when that ships.
    default_annual_entitlement_days NUMERIC(6,2),

    -- NULL = unlimited carry-forward, 0 = none, > 0 = capped.
    carry_forward_limit_days        NUMERIC(6,2),

    max_consecutive_days            INTEGER,
    exclude_weekends                BOOLEAN      NOT NULL DEFAULT FALSE,
    exclude_holidays                BOOLEAN      NOT NULL DEFAULT FALSE,

    active                          BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                      VARCHAR(120),
    updated_by                      VARCHAR(120)
);

CREATE TABLE leave_mgmt.leave_balance (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id              UUID         NOT NULL,
    leave_type_id            UUID         NOT NULL REFERENCES leave_mgmt.leave_type (id),
    year                     INTEGER      NOT NULL,

    entitlement_days         NUMERIC(7,2) NOT NULL DEFAULT 0,
    carried_forward_days     NUMERIC(7,2) NOT NULL DEFAULT 0,
    adjustment_days          NUMERIC(7,2) NOT NULL DEFAULT 0,
    used_days                NUMERIC(7,2) NOT NULL DEFAULT 0,
    reserved_days            NUMERIC(7,2) NOT NULL DEFAULT 0,

    last_recalculated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (employee_id, leave_type_id, year),
    CONSTRAINT chk_balance_non_negative CHECK (
        used_days >= 0 AND reserved_days >= 0
    )
);

CREATE INDEX idx_balance_employee_year ON leave_mgmt.leave_balance (employee_id, year);
CREATE INDEX idx_balance_type          ON leave_mgmt.leave_balance (leave_type_id);

CREATE TABLE leave_mgmt.leave_request (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    request_no               VARCHAR(32)  NOT NULL UNIQUE,

    employee_id              UUID         NOT NULL,
    leave_type_id            UUID         NOT NULL REFERENCES leave_mgmt.leave_type (id),

    start_date               DATE         NOT NULL,
    end_date                 DATE         NOT NULL,
    half_day                 BOOLEAN      NOT NULL DEFAULT FALSE,
    total_days               NUMERIC(7,2) NOT NULL,

    reason                   TEXT,
    attachment_url           VARCHAR(500),
    replacement_employee_id  UUID,

    -- DRAFT, PENDING, APPROVED, REJECTED, CANCELLED
    status                   VARCHAR(32)  NOT NULL,
    workflow_instance_id     UUID,

    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               VARCHAR(120),

    CONSTRAINT chk_request_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_request_days  CHECK (total_days > 0)
);

CREATE INDEX idx_leave_request_employee ON leave_mgmt.leave_request (employee_id, start_date);
CREATE INDEX idx_leave_request_status   ON leave_mgmt.leave_request (status);
CREATE INDEX idx_leave_request_type     ON leave_mgmt.leave_request (leave_type_id);
