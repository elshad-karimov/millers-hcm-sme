-- Workflow Engine foundation (PRD Section 9).
-- This iteration ships sequential approvals; parallel / conditional /
-- delegation / SLA / escalation are deliberate later-work seams (see README).

CREATE TABLE workflow.workflow_definition (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(64)  NOT NULL UNIQUE,
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    auto_approve  BOOLEAN      NOT NULL DEFAULT FALSE,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE workflow.workflow_step (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    definition_id   UUID         NOT NULL REFERENCES workflow.workflow_definition (id) ON DELETE CASCADE,
    step_order      INTEGER      NOT NULL,
    name            VARCHAR(160) NOT NULL,
    approver_role   VARCHAR(64)  NOT NULL,   -- Spring authority, e.g. ROLE_HR_ADMIN
    sla_hours       INTEGER,
    UNIQUE (definition_id, step_order)
);

CREATE INDEX idx_workflow_step_definition ON workflow.workflow_step (definition_id);

CREATE TABLE workflow.workflow_instance (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    definition_id        UUID         NOT NULL REFERENCES workflow.workflow_definition (id),
    definition_code      VARCHAR(64)  NOT NULL,   -- denormalized for fast inbox filtering

    subject_module       VARCHAR(64)  NOT NULL,   -- e.g. ORGANIZATION
    subject_entity       VARCHAR(64)  NOT NULL,   -- e.g. StructureVersion
    subject_id           VARCHAR(64)  NOT NULL,
    title                VARCHAR(300) NOT NULL,

    current_step_index   INTEGER      NOT NULL,
    current_step_role    VARCHAR(64),

    -- PENDING, APPROVED, REJECTED, RETURNED, CANCELLED, AUTO_APPROVED
    status               VARCHAR(32)  NOT NULL,

    initiated_by         VARCHAR(120) NOT NULL,
    initiated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at         TIMESTAMPTZ,
    payload              JSONB
);

CREATE INDEX idx_wf_instance_subject  ON workflow.workflow_instance (subject_module, subject_entity, subject_id);
CREATE INDEX idx_wf_instance_status   ON workflow.workflow_instance (status);
CREATE INDEX idx_wf_instance_pending  ON workflow.workflow_instance (current_step_role) WHERE status = 'PENDING';

CREATE TABLE workflow.workflow_action (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id UUID         NOT NULL REFERENCES workflow.workflow_instance (id) ON DELETE CASCADE,
    step_index  INTEGER      NOT NULL,
    step_name   VARCHAR(160),

    -- APPROVE, REJECT, RETURN, COMMENT, CANCEL, START, AUTO_APPROVE
    action      VARCHAR(32)  NOT NULL,
    actor       VARCHAR(120) NOT NULL,
    comment     TEXT,
    ip_address  VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_wf_action_instance ON workflow.workflow_action (instance_id);
CREATE INDEX idx_wf_action_actor    ON workflow.workflow_action (actor);
