-- V266: M446 warnings

CREATE TABLE employee_relations.warning_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    employee_id UUID NOT NULL,
    warning_level VARCHAR(30) NOT NULL, -- VERBAL, FIRST_WRITTEN, SECOND_WRITTEN, FINAL
    disciplinary_action_id UUID, -- link to M67 disciplinary_action if issued from one
    reason TEXT NOT NULL,

    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ, -- NULL for FINAL warnings (indefinite)

    is_acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged_at TIMESTAMPTZ,

    attachment_id UUID, -- soft FK to core_hr.attachment

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL
);

CREATE INDEX idx_warning_tenant_employee ON employee_relations.warning_record(tenant_id, employee_id);
CREATE INDEX idx_warning_expires ON employee_relations.warning_record(expires_at) WHERE expires_at IS NOT NULL;

COMMENT ON TABLE employee_relations.warning_record IS 'M446 — Employee warnings (VERBAL → FIRST → SECOND → FINAL)';
COMMENT ON COLUMN employee_relations.warning_record.expires_at IS 'NULL = indefinite (FINAL warnings); default: VERBAL +6mo, FIRST +12mo, SECOND +18mo';
