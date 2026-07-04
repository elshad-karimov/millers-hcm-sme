-- M123 — Leave blackout windows.
--
-- HR sets date ranges where leave is either blocked (BLOCK) or routed
-- through a stricter approval path (REQUIRES_APPROVAL). Scope is one
-- of GLOBAL (everyone), ORG_UNIT (the unit + descendants), or
-- LEAVE_TYPE (one type only). LeaveRequestService.submit consults this
-- table and either rejects the request with a friendly message naming
-- the conflict, or flags it so the workflow engine routes to HR.

CREATE TABLE leave_mgmt.blackout_window (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL,
    description     TEXT,

    -- GLOBAL, ORG_UNIT, LEAVE_TYPE — the dimension the window applies on.
    scope           VARCHAR(24)  NOT NULL,

    -- Used iff scope = ORG_UNIT. Soft ref (no FK) so deleting an org unit
    -- doesn't blow up an audit-bearing blackout row; the service silently
    -- skips windows whose unit no longer resolves.
    org_unit_id     UUID,

    -- Used iff scope = LEAVE_TYPE. Soft ref for the same reason.
    leave_type_id   UUID,

    start_date      DATE         NOT NULL,
    end_date        DATE         NOT NULL,

    -- BLOCK: hard reject. REQUIRES_APPROVAL: workflow flag (Phase 1
    -- shows the flag; the actual HR-routing wiring is a follow-up).
    severity        VARCHAR(24)  NOT NULL DEFAULT 'BLOCK',

    reason          TEXT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(160) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      VARCHAR(160),

    CONSTRAINT chk_blackout_dates    CHECK (end_date >= start_date),
    CONSTRAINT chk_blackout_severity CHECK (severity IN ('BLOCK', 'REQUIRES_APPROVAL')),
    CONSTRAINT chk_blackout_scope    CHECK (scope IN ('GLOBAL', 'ORG_UNIT', 'LEAVE_TYPE')),
    -- ORG_UNIT scope must specify a unit; LEAVE_TYPE scope must specify a type.
    CONSTRAINT chk_blackout_scope_id CHECK (
        (scope = 'ORG_UNIT'   AND org_unit_id   IS NOT NULL AND leave_type_id IS NULL)
     OR (scope = 'LEAVE_TYPE' AND leave_type_id IS NOT NULL AND org_unit_id   IS NULL)
     OR (scope = 'GLOBAL'     AND org_unit_id   IS NULL     AND leave_type_id IS NULL))
);

-- Hot path: "give me every active window overlapping [from, to]". A composite
-- index on (active, end_date) lets the planner drop everything that ended
-- before the request's start date.
CREATE INDEX ix_blackout_active_range
    ON leave_mgmt.blackout_window (end_date)
    WHERE active = TRUE;

CREATE INDEX ix_blackout_org_unit   ON leave_mgmt.blackout_window (org_unit_id);
CREATE INDEX ix_blackout_leave_type ON leave_mgmt.blackout_window (leave_type_id);

-- Mark on the leave request that it was routed through stricter approval
-- because of a REQUIRES_APPROVAL window. Reporting / audit hook.
ALTER TABLE leave_mgmt.leave_request
    ADD COLUMN blackout_flag BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX ix_leave_request_blackout_flag
    ON leave_mgmt.leave_request (blackout_flag)
    WHERE blackout_flag = TRUE;
