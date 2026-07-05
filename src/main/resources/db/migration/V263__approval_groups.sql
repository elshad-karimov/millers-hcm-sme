-- V263: M443 approval groups

CREATE TABLE workflow.approval_group (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(255),

    UNIQUE(tenant_id, code)
);

CREATE TABLE workflow.approval_group_member (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    group_id UUID NOT NULL,
    username VARCHAR(255) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL,

    CONSTRAINT fk_approval_group_member_group FOREIGN KEY (group_id) REFERENCES workflow.approval_group(id) ON DELETE CASCADE,
    UNIQUE(group_id, username)
);

CREATE INDEX idx_approval_group_tenant ON workflow.approval_group(tenant_id, active);
CREATE INDEX idx_approval_group_member_group ON workflow.approval_group_member(group_id);
CREATE INDEX idx_approval_group_member_username ON workflow.approval_group_member(tenant_id, username);

-- Add approval_group_id to workflow_step
ALTER TABLE workflow.workflow_step
ADD COLUMN approval_group_id UUID,
ADD CONSTRAINT fk_workflow_step_approval_group FOREIGN KEY (approval_group_id) REFERENCES workflow.approval_group(id);

CREATE INDEX idx_workflow_step_approval_group ON workflow.workflow_step(approval_group_id);

COMMENT ON TABLE workflow.approval_group IS 'M443 — Approval groups for workflow steps (any member may act)';
COMMENT ON COLUMN workflow.workflow_step.approval_group_id IS 'M443 — If set, eligible actors = group members (any may act)';
