-- M345 — Unauthorized Absence → Leave Conversion
-- Tracks HR decisions on ABSENT attendance days:
-- convert to leave or formally dismiss (accept as absence).
CREATE TABLE IF NOT EXISTS leave_mgmt.unauthorized_absence_conversion (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    employee_id     UUID        NOT NULL,
    absence_date    DATE        NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- Set when HR decides to convert to a leave type
    leave_type_id   UUID        REFERENCES leave_mgmt.leave_type(id),
    leave_request_id UUID,
    -- Set when HR dismisses (records absence without leave conversion)
    notes           TEXT,
    resolved_by     VARCHAR(160),
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(160),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uac_status_chk CHECK (status IN ('PENDING','CONVERTED','DISMISSED')),
    UNIQUE (employee_id, absence_date)
);

CREATE INDEX IF NOT EXISTS ix_uac_employee_date
    ON leave_mgmt.unauthorized_absence_conversion (employee_id, absence_date);

CREATE INDEX IF NOT EXISTS ix_uac_status
    ON leave_mgmt.unauthorized_absence_conversion (status)
    WHERE status = 'PENDING';
