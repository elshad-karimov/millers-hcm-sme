-- M105/M106 — Onboarding + offboarding checklists.
--
-- Templates define an ordered list of tasks (equipment provisioning,
-- buddy assignment, IT account creation, exit interview, etc.) that get
-- copied to a per-employee assignment when an employee is hired or
-- terminated. Per-task status (PENDING / IN_PROGRESS / DONE / SKIPPED)
-- + assigned-to user (HR/IT/manager) lives on the task-status rows.

-- Template (single source of truth — HR clones to assignment on hire/terminate).
CREATE TABLE lifecycle.checklist_template (
    id              uuid PRIMARY KEY,
    code            varchar(40) NOT NULL UNIQUE,
    name            varchar(200) NOT NULL,
    description     text,
    flow_type       varchar(20) NOT NULL,    -- ONBOARDING or OFFBOARDING
    active          boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT checklist_template_flow_check
        CHECK (flow_type IN ('ONBOARDING','OFFBOARDING'))
);

-- Template task — ordered list of items.
CREATE TABLE lifecycle.checklist_template_task (
    id              uuid PRIMARY KEY,
    template_id     uuid NOT NULL REFERENCES lifecycle.checklist_template(id) ON DELETE CASCADE,
    step_order      int NOT NULL,
    title           varchar(200) NOT NULL,
    description     text,
    default_owner_role varchar(40),   -- HR / IT / MANAGER / FINANCE / EMPLOYEE — informational
    due_offset_days int DEFAULT 0,    -- relative to start_date or end_date
    required        boolean NOT NULL DEFAULT true,
    CONSTRAINT checklist_template_task_unique_order
        UNIQUE (template_id, step_order)
);

-- Per-employee assignment — created when HR triggers onboarding/offboarding.
CREATE TABLE lifecycle.checklist_assignment (
    id              uuid PRIMARY KEY,
    template_id     uuid NOT NULL REFERENCES lifecycle.checklist_template(id),
    employee_id     uuid NOT NULL,
    flow_type       varchar(20) NOT NULL,   -- ONBOARDING or OFFBOARDING
    anchor_date     date NOT NULL,          -- hire date (onboarding) or last day (offboarding)
    status          varchar(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at      timestamptz NOT NULL DEFAULT now(),
    completed_at    timestamptz,
    started_by      varchar(80),
    notes           text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT checklist_assignment_flow_check
        CHECK (flow_type IN ('ONBOARDING','OFFBOARDING')),
    CONSTRAINT checklist_assignment_status_check
        CHECK (status IN ('IN_PROGRESS','COMPLETED','CANCELLED'))
);

-- Partial unique index: at most one active (IN_PROGRESS) checklist per
-- (employee, flow_type) pair. An employee can be re-onboarded after a
-- rehire (M78) — the prior assignment must be COMPLETED or CANCELLED first.
CREATE UNIQUE INDEX checklist_assignment_active_unique
    ON lifecycle.checklist_assignment (employee_id, flow_type)
    WHERE status = 'IN_PROGRESS';

CREATE INDEX checklist_assignment_employee_idx
    ON lifecycle.checklist_assignment (employee_id, status);

-- Per-task status on an assignment (one row per template task at creation time).
CREATE TABLE lifecycle.checklist_task_status (
    id              uuid PRIMARY KEY,
    assignment_id   uuid NOT NULL REFERENCES lifecycle.checklist_assignment(id) ON DELETE CASCADE,
    template_task_id uuid NOT NULL REFERENCES lifecycle.checklist_template_task(id),
    step_order      int NOT NULL,
    title           varchar(200) NOT NULL,
    description     text,
    owner_role      varchar(40),
    assigned_to_username varchar(80),
    due_date        date,
    required        boolean NOT NULL DEFAULT true,
    status          varchar(20) NOT NULL DEFAULT 'PENDING',
    completed_at    timestamptz,
    completed_by    varchar(80),
    notes           text,
    CONSTRAINT checklist_task_status_check
        CHECK (status IN ('PENDING','IN_PROGRESS','DONE','SKIPPED'))
);

CREATE INDEX checklist_task_status_assignment_idx
    ON lifecycle.checklist_task_status (assignment_id, step_order);

COMMENT ON TABLE lifecycle.checklist_template IS
    'M105/M106 — Onboarding / offboarding checklist template.';
COMMENT ON TABLE lifecycle.checklist_assignment IS
    'M105/M106 — Per-employee instantiation of a checklist template.';
