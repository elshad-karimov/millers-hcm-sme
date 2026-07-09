-- ----------------------------------------------------------------------------
-- M458 — HCM_29 Phase A.3: Asset damage/loss case with deduction bridge.
--
-- On APPROVE with deduction_proposed=true: create a PayrollDeduction row via
-- PayrollAdvanceLoanDeductionService (non-fatal bridge). deduction_amount and
-- deduction_proposed are HR/Finance-only (confidential).
-- ----------------------------------------------------------------------------

CREATE SEQUENCE IF NOT EXISTS lifecycle.asset_damage_loss_case_no_seq START 1;

CREATE TABLE IF NOT EXISTS lifecycle.asset_damage_loss_case (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               varchar(80)  NOT NULL DEFAULT 'default',
    case_no                 varchar(20)  NOT NULL UNIQUE,
    asset_id                uuid         NOT NULL REFERENCES core_hr.employee_asset(id),
    employee_id             uuid         NOT NULL,
    case_type               varchar(20)  NOT NULL,
    description             text,
    estimated_amount        numeric(12,2),
    deduction_proposed      boolean      NOT NULL DEFAULT false,
    deduction_amount        numeric(12,2),
    payroll_deduction_id    uuid,
    status                  varchar(20)  NOT NULL DEFAULT 'OPEN',
    reported_by             varchar(80),
    reported_at             timestamptz  NOT NULL DEFAULT now(),
    approved_by             varchar(80),
    approved_at             timestamptz,
    notes                   text,
    created_at              timestamptz  NOT NULL DEFAULT now(),
    created_by              varchar(80),
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    updated_by              varchar(80),
    CONSTRAINT asset_damage_loss_case_type_check
        CHECK (case_type IN ('DAMAGE', 'LOSS')),
    CONSTRAINT asset_damage_loss_case_status_check
        CHECK (status IN ('OPEN', 'APPROVED', 'REJECTED', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS asset_damage_loss_case_tenant_idx ON lifecycle.asset_damage_loss_case (tenant_id);
CREATE INDEX IF NOT EXISTS asset_damage_loss_case_asset_idx ON lifecycle.asset_damage_loss_case (asset_id);
CREATE INDEX IF NOT EXISTS asset_damage_loss_case_employee_idx ON lifecycle.asset_damage_loss_case (employee_id);
CREATE INDEX IF NOT EXISTS asset_damage_loss_case_status_idx ON lifecycle.asset_damage_loss_case (status);

COMMENT ON TABLE lifecycle.asset_damage_loss_case IS
    'M458 — Asset damage/loss cases. On APPROVE with deduction: create PayrollDeduction (non-fatal). Amounts HR/Finance-only.';
