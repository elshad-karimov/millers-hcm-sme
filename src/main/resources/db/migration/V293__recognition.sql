-- M478 — Peer recognition/kudos

CREATE TABLE engagement.recognition (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(100) NOT NULL DEFAULT 'default',
    from_employee_id    UUID NOT NULL REFERENCES core_hr.employee(id),
    to_employee_id      UUID NOT NULL REFERENCES core_hr.employee(id),
    value_tag           VARCHAR(50) NOT NULL, -- TEAMWORK | INNOVATION | EXCELLENCE | CUSTOMER_FOCUS | LEADERSHIP
    message             VARCHAR(1000) NOT NULL,
    visibility          VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', -- PUBLIC | PRIVATE
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | HIDDEN
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (from_employee_id <> to_employee_id)
);

CREATE INDEX idx_recognition_tenant ON engagement.recognition(tenant_id);
CREATE INDEX idx_recognition_to_employee ON engagement.recognition(to_employee_id);
CREATE INDEX idx_recognition_from_employee ON engagement.recognition(from_employee_id);
CREATE INDEX idx_recognition_status_visibility ON engagement.recognition(status, visibility);
