-- ----------------------------------------------------------------------------
-- M301 — Onboarding PRD Phase B.1: equipment / workspace onboarding tasks
-- create a tracked resource (provisioning) request.
--
-- When an onboarding checklist starts, each task typed EQUIPMENT_REQUEST or
-- WORKSPACE_REQUEST spawns a resource request that IT / Facilities work
-- (REQUESTED → IN_PROGRESS → FULFILLED, or CANCELLED). Fulfilling an EQUIPMENT
-- request bridges to the M72/M124 asset module: it creates an EmployeeAsset
-- (handover) and links it here, then marks the originating checklist task DONE.
--
-- The table is deliberately generic (a `category` column) so Phase B.2 can
-- reuse it for IT-account / access-card requests without a new migration.
-- ----------------------------------------------------------------------------

CREATE SEQUENCE IF NOT EXISTS lifecycle.onboarding_resource_request_no_seq START 1;

CREATE TABLE IF NOT EXISTS lifecycle.onboarding_resource_request (
    id              uuid PRIMARY KEY,
    request_no      varchar(20)  NOT NULL UNIQUE,
    employee_id     uuid         NOT NULL,
    assignment_id   uuid         REFERENCES lifecycle.checklist_assignment(id) ON DELETE CASCADE,
    -- The originating checklist task; UNIQUE so a restart can't double-create.
    task_status_id  uuid         REFERENCES lifecycle.checklist_task_status(id) ON DELETE SET NULL,
    category        varchar(20)  NOT NULL,   -- EQUIPMENT | WORKSPACE | IT_ACCOUNT | ACCESS_CARD | OTHER
    title           varchar(200) NOT NULL,
    details         text,
    status          varchar(20)  NOT NULL DEFAULT 'REQUESTED',
    asset_id        uuid,                    -- linked EmployeeAsset once fulfilled (EQUIPMENT)
    requested_at    timestamptz  NOT NULL DEFAULT now(),
    fulfilled_at    timestamptz,
    created_by      varchar(80),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      varchar(80),
    CONSTRAINT onboarding_resource_request_category_check
        CHECK (category IN ('EQUIPMENT','WORKSPACE','IT_ACCOUNT','ACCESS_CARD','OTHER')),
    CONSTRAINT onboarding_resource_request_status_check
        CHECK (status IN ('REQUESTED','IN_PROGRESS','FULFILLED','CANCELLED'))
);

-- At most one request per checklist task (idempotent auto-create on restart).
CREATE UNIQUE INDEX IF NOT EXISTS onboarding_resource_request_task_unique
    ON lifecycle.onboarding_resource_request (task_status_id)
    WHERE task_status_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS onboarding_resource_request_employee_idx
    ON lifecycle.onboarding_resource_request (employee_id);
CREATE INDEX IF NOT EXISTS onboarding_resource_request_status_idx
    ON lifecycle.onboarding_resource_request (status, category);

COMMENT ON TABLE lifecycle.onboarding_resource_request IS
    'M301 — onboarding provisioning request (equipment/workspace; extensible to IT/access in B.2). Fulfilling EQUIPMENT bridges to core_hr.employee_asset.';
