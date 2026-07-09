-- ----------------------------------------------------------------------------
-- M457 — HCM_29 Phase A.2: Asset Transfer (PENDING→APPROVED→COMPLETED).
--
-- Simple HR-approve flow. On COMPLETED: reassign the asset + create event
-- history on both sides (from_employee, to_employee). No workflow machinery —
-- status transitions via service methods (per analysis.md).
-- ----------------------------------------------------------------------------

CREATE SEQUENCE IF NOT EXISTS lifecycle.asset_transfer_no_seq START 1;

CREATE TABLE IF NOT EXISTS lifecycle.asset_transfer (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               varchar(80)  NOT NULL DEFAULT 'default',
    transfer_no             varchar(20)  NOT NULL UNIQUE,
    asset_id                uuid         NOT NULL REFERENCES lifecycle.employee_asset(id),
    from_employee_id        uuid         NOT NULL,
    to_employee_id          uuid         NOT NULL,
    reason                  varchar(1000),
    status                  varchar(20)  NOT NULL DEFAULT 'PENDING',
    requested_by            varchar(80),
    requested_at            timestamptz  NOT NULL DEFAULT now(),
    approved_by             varchar(80),
    approved_at             timestamptz,
    completed_at            timestamptz,
    notes                   text,
    created_at              timestamptz  NOT NULL DEFAULT now(),
    created_by              varchar(80),
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    updated_by              varchar(80),
    CONSTRAINT asset_transfer_status_check
        CHECK (status IN ('PENDING', 'APPROVED', 'COMPLETED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS asset_transfer_tenant_idx ON lifecycle.asset_transfer (tenant_id);
CREATE INDEX IF NOT EXISTS asset_transfer_asset_idx ON lifecycle.asset_transfer (asset_id);
CREATE INDEX IF NOT EXISTS asset_transfer_from_employee_idx ON lifecycle.asset_transfer (from_employee_id);
CREATE INDEX IF NOT EXISTS asset_transfer_to_employee_idx ON lifecycle.asset_transfer (to_employee_id);
CREATE INDEX IF NOT EXISTS asset_transfer_status_idx ON lifecycle.asset_transfer (status);

COMMENT ON TABLE lifecycle.asset_transfer IS
    'M457 — Asset transfer requests between employees. HR approve then complete → reassign asset + event history.';
