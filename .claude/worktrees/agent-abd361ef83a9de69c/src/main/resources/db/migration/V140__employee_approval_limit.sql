-- ----------------------------------------------------------------------------
-- M261 — Phase F.7: Employee approval limit (PRD §27).
--
-- Tracks the maximum monetary authority an employee has for various
-- approval types (purchase orders, expense reports, contracts, etc).
-- Used by the M248 position profile auto-grant — when a manager is
-- hired into a position with an APPROVAL_LIMIT profile item set,
-- the corresponding row is created here automatically; on
-- termination it gets effective-dated out.
--
-- Effective-dated rows so historical limits survive promotion /
-- demotion without losing the audit trail.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS core_hr.employee_approval_limit (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     UUID         NOT NULL,

    /** PURCHASE_ORDER / EXPENSE_REPORT / CONTRACT / INVOICE / TRAVEL / GENERAL */
    limit_type      VARCHAR(32)  NOT NULL,

    max_amount      NUMERIC(14,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL DEFAULT 'AZN',

    effective_from  DATE          NOT NULL,
    effective_to    DATE,             -- NULL = currently active

    /** PROFILE_GRANT / MANUAL — where the row came from. */
    source          VARCHAR(32)   NOT NULL DEFAULT 'MANUAL',
    /** Back-link to the M248 PositionProfileGrant row when source=PROFILE_GRANT. */
    source_grant_id UUID,

    notes           TEXT,

    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      VARCHAR(120),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by      VARCHAR(120),

    CONSTRAINT chk_employee_approval_limit_amount CHECK (max_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_employee_approval_limit_employee
    ON core_hr.employee_approval_limit(employee_id);

-- Active rows only — the SPA panel + downstream consumers care almost
-- exclusively about currently-active limits.
CREATE INDEX IF NOT EXISTS idx_employee_approval_limit_active
    ON core_hr.employee_approval_limit(employee_id, limit_type)
    WHERE effective_to IS NULL;
