-- M142 — HRBP assignment (§24 audit closure).
--
-- HR Business Partner assignment was missing entirely: no hrbp_id on
-- org_unit, no assignment registry, and no workflow routing path for
-- "route to the employee's HRBP" steps.
--
-- Three changes:
--   1. organization.hr_partner — the full assignment registry with
--      effective dates, backup flag, multi-HRBP-per-unit support.
--   2. organization.org_unit.hrbp_id — the primary HRBP quick-reference
--      (soft FK → core_hr.employee). Workflow resolution uses this first
--      then walks up the org tree until a non-null hrbp_id is found.
--   3. workflow.workflow_step.resolves_to_hrbp — analogous to the
--      existing resolves_to_manager flag; when true only the resolved
--      HRBP for the subject's org unit can act on the step.

-- ── 1. HR Partner assignment registry ────────────────────────────────
CREATE TABLE IF NOT EXISTS organization.hr_partner (
    id              UUID         PRIMARY KEY,
    -- Soft FK to organization.org_unit. Not a hard FK because org units
    -- are version-scoped.
    org_unit_id     UUID         NOT NULL,
    -- The HR Business Partner — a Millers HCM employee.
    employee_id     UUID         NOT NULL,
    -- When TRUE this is the backup HRBP (primary is FALSE).
    is_backup       BOOLEAN      NOT NULL DEFAULT FALSE,
    effective_from  DATE,
    effective_to    DATE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(80),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      VARCHAR(80),

    CONSTRAINT chk_hrbp_window
        CHECK (effective_to IS NULL OR effective_from IS NULL
            OR effective_to >= effective_from),
    -- One employee can be assigned as HRBP (primary or backup) to a
    -- given org unit at most once per effective_from date.
    CONSTRAINT uq_hr_partner_unit_emp_from
        UNIQUE (org_unit_id, employee_id, effective_from)
);

CREATE INDEX IF NOT EXISTS idx_hr_partner_org_unit
    ON organization.hr_partner (org_unit_id, active);
CREATE INDEX IF NOT EXISTS idx_hr_partner_employee
    ON organization.hr_partner (employee_id);

-- ── 2. Primary HRBP quick-reference on org_unit ───────────────────────
-- Nullable — existing rows are unchanged. The service layer prefers this
-- FK for workflow resolution; falls back to walking up the org tree.
ALTER TABLE organization.org_unit
    ADD COLUMN IF NOT EXISTS hrbp_id UUID;

CREATE INDEX IF NOT EXISTS idx_org_unit_hrbp
    ON organization.org_unit (hrbp_id)
    WHERE hrbp_id IS NOT NULL;

-- ── 3. Workflow step: resolves_to_hrbp flag ────────────────────────────
-- Mirrors resolves_to_manager. When TRUE the workflow engine routes the
-- step to the HRBP resolved for the subject's org unit rather than
-- pooling it to everyone with the approver_role.
ALTER TABLE workflow.workflow_step
    ADD COLUMN IF NOT EXISTS resolves_to_hrbp BOOLEAN NOT NULL DEFAULT FALSE;
