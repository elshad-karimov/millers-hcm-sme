-- ----------------------------------------------------------------------------
-- M250 — Phase F.2: Position profile grants.
--
-- One row per (occupancy × mandatory profile item). Created automatically
-- when a PRIMARY occupancy is opened (M249 / D.2 wired through
-- EmployeeService.create / update / TerminationService.process).
--
-- The grant row is the visible deliverable: HR sees "this new occupant
-- needs these 7 things set up" without having to remember every
-- per-position requirement. Status moves through:
--
--   PENDING   — auto-created on hire; HR has not yet actioned
--   ACTIVE    — operator confirmed the underlying grant was performed
--   REVOKED   — occupancy ended (employee left) and the grant was pulled
--   FAILED    — auto-grant attempt failed (Phase F.3 will populate this)
--
-- Snapshot fields (label, value_amount, currency, reference_code) are
-- copied from the profile item at grant time so editing the profile
-- later doesn't retroactively change historical grants.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS staffing.position_profile_grant (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occupancy_id      UUID NOT NULL REFERENCES staffing.position_occupancy(id) ON DELETE CASCADE,
    profile_item_id   UUID REFERENCES staffing.position_profile_item(id) ON DELETE SET NULL,
    employee_id       UUID NOT NULL REFERENCES core_hr.employee(id),
    position_id       UUID NOT NULL REFERENCES staffing.position(id),
    -- Snapshot of the profile item at the time of grant.
    item_type         VARCHAR(32)  NOT NULL,
    label             VARCHAR(200) NOT NULL,
    value_amount      NUMERIC(14,2),
    currency          VARCHAR(3),
    reference_code    VARCHAR(120),
    notes             TEXT,
    -- Grant lifecycle.
    status            VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    granted_at        TIMESTAMPTZ,
    granted_by        VARCHAR(120),
    revoked_at        TIMESTAMPTZ,
    revoked_by        VARCHAR(120),
    revoke_reason     TEXT,
    failure_reason    TEXT,
    -- Audit
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        VARCHAR(120),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by        VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_profile_grant_occupancy
    ON staffing.position_profile_grant(occupancy_id);
CREATE INDEX IF NOT EXISTS idx_profile_grant_employee
    ON staffing.position_profile_grant(employee_id, status);
CREATE INDEX IF NOT EXISTS idx_profile_grant_status
    ON staffing.position_profile_grant(status);
