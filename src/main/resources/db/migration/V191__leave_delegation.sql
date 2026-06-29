-- Leave delegation: who covers an employee's responsibilities during leave
CREATE TABLE IF NOT EXISTS leave_mgmt.leave_delegation (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    leave_request_id UUID         NOT NULL REFERENCES leave_mgmt.leave_request(id),
    delegator_id     UUID         NOT NULL,
    delegate_id      UUID         NOT NULL,
    delegation_scope TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT leave_delegation_status_chk CHECK (status IN ('PENDING','ACCEPTED','DECLINED','REVOKED')),
    delegate_notes   TEXT,
    responded_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       VARCHAR(160),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT leave_delegation_different_chk CHECK (delegator_id <> delegate_id),
    UNIQUE (leave_request_id, delegate_id)
);

CREATE INDEX IF NOT EXISTS ix_leave_delegation_request ON leave_mgmt.leave_delegation (leave_request_id);
CREATE INDEX IF NOT EXISTS ix_leave_delegation_delegate ON leave_mgmt.leave_delegation (delegate_id) WHERE status = 'PENDING';
