-- ----------------------------------------------------------------------------
-- M432 — HCM_22 MSS Phase E.1: employee movement requests (TRANSFER/PROMOTION).
--
-- Managers initiate movement requests for their direct reports (AccessScope).
-- EMP_MOVEMENT workflow (manager→HR). HR approves but does NOT auto-execute
-- the transfer/promotion; note for HR to execute via existing tooling.
-- proposed_salary visible ONLY to HR in responses (non-HR: null).
-- ----------------------------------------------------------------------------

CREATE SEQUENCE IF NOT EXISTS lifecycle.employee_movement_request_no_seq START 1;

CREATE TABLE IF NOT EXISTS lifecycle.employee_movement_request (
    id                      uuid PRIMARY KEY,
    tenant_id               varchar(80)  NOT NULL DEFAULT 'default',
    request_no              varchar(20)  NOT NULL UNIQUE,
    employee_id             uuid         NOT NULL,
    movement_type           varchar(20)  NOT NULL,
    proposed_position_id    uuid,
    proposed_org_unit_id    uuid,
    proposed_grade          varchar(50),
    proposed_salary         numeric(12,2),
    effective_date          date         NOT NULL,
    justification           varchar(2000),
    status                  varchar(20)  NOT NULL DEFAULT 'DRAFT',
    workflow_instance_id    uuid,
    requested_by            varchar(80),
    created_at              timestamptz  NOT NULL DEFAULT now(),
    created_by              varchar(80),
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    updated_by              varchar(80),
    CONSTRAINT employee_movement_request_movement_type_check
        CHECK (movement_type IN ('TRANSFER', 'PROMOTION')),
    CONSTRAINT employee_movement_request_status_check
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS employee_movement_request_tenant_idx
    ON lifecycle.employee_movement_request (tenant_id);
CREATE INDEX IF NOT EXISTS employee_movement_request_employee_idx
    ON lifecycle.employee_movement_request (employee_id);
CREATE INDEX IF NOT EXISTS employee_movement_request_status_idx
    ON lifecycle.employee_movement_request (status);

COMMENT ON TABLE lifecycle.employee_movement_request IS
    'M432 — Manager-initiated employee movement requests (transfer/promotion). EMP_MOVEMENT workflow. HR approves, then executes manually.';

-- ── Seed EMP_MOVEMENT workflow (HR_ADMIN step) ──────────────────────────────
INSERT INTO workflow.workflow_definition (id, definition_code, definition_name, version, description, active, created_at, created_by, updated_at, updated_by)
VALUES (
    gen_random_uuid(),
    'EMP_MOVEMENT',
    'Employee Movement Approval',
    1,
    'M432 — Manager-initiated transfer/promotion request approval. Single HR_ADMIN step.',
    true,
    now(),
    'system',
    now(),
    'system'
) ON CONFLICT (definition_code, version) DO NOTHING;

INSERT INTO workflow.workflow_step (id, workflow_definition_id, step_order, step_code, step_name, role_code, is_final, created_at, created_by, updated_at, updated_by)
SELECT
    gen_random_uuid(),
    wd.id,
    1,
    'HR_APPROVAL',
    'HR Approval',
    'HR_ADMIN',
    true,
    now(),
    'system',
    now(),
    'system'
FROM workflow.workflow_definition wd
WHERE wd.definition_code = 'EMP_MOVEMENT' AND wd.version = 1
ON CONFLICT DO NOTHING;
