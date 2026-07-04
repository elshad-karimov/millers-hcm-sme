-- M328–M337: Attendance operations — corrections, overtime, period locks, exceptions, devices, workspace
-- Time & Attendance module completion

-- M329: Overtime Request
CREATE TABLE attendance.overtime_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    work_date DATE NOT NULL,
    summary_id UUID,
    ot_start TIMESTAMPTZ NOT NULL,
    ot_end TIMESTAMPTZ NOT NULL,
    requested_minutes INT NOT NULL,
    reason TEXT NOT NULL,
    pre_approved BOOLEAN NOT NULL DEFAULT FALSE,
    workflow_instance_id UUID,
    workflow_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    decision VARCHAR(30),
    decision_comment TEXT,
    decided_at TIMESTAMPTZ,
    decided_by VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(200),
    updated_by VARCHAR(200)
);
CREATE INDEX idx_ot_req_tenant_emp ON attendance.overtime_request (tenant_id, employee_id);
CREATE INDEX idx_ot_req_work_date ON attendance.overtime_request (work_date);
CREATE INDEX idx_ot_req_wf_status ON attendance.overtime_request (workflow_status);

-- M330: Attendance Period Lock
CREATE TABLE attendance.attendance_period (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    year INT NOT NULL,
    month INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(200),
    unlocked_at TIMESTAMPTZ,
    unlocked_by VARCHAR(200),
    employee_count_at_lock INT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_attendance_period UNIQUE (tenant_id, year, month)
);
CREATE INDEX idx_att_period_tenant ON attendance.attendance_period (tenant_id);

-- M332: Exception Configuration
CREATE TABLE attendance.exception_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    exception_type VARCHAR(50) NOT NULL,
    threshold_minutes INT NOT NULL DEFAULT 0,
    severity VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auto_notify BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_exception_config UNIQUE (tenant_id, exception_type)
);

-- Seed 15 default exception types for all legal entities
INSERT INTO attendance.exception_config (id, tenant_id, exception_type, threshold_minutes, severity, enabled, auto_notify)
SELECT gen_random_uuid(), le.id, et.exception_type, et.threshold_minutes, et.severity, true, et.auto_notify
FROM organization.legal_entity le
CROSS JOIN (VALUES
    ('LATE_ARRIVAL',         10, 'WARNING',  false),
    ('EARLY_DEPARTURE',       5, 'WARNING',  false),
    ('ABSENT',                0, 'CRITICAL', true),
    ('MISSING_CLOCK_OUT',     0, 'WARNING',  true),
    ('UNAUTHORIZED_OT',      30, 'WARNING',  false),
    ('EXCESS_OT',           180, 'CRITICAL', true),
    ('CONSECUTIVE_LATES',     0, 'CRITICAL', true),
    ('PARTIAL_DAY',          60, 'INFO',     false),
    ('SHIFT_MISMATCH',        0, 'INFO',     false),
    ('MISSED_BREAK',          0, 'INFO',     false),
    ('POLICY_GRACE_EXCEEDED', 0, 'WARNING',  false),
    ('ROSTER_DEVIATION',      0, 'WARNING',  false),
    ('WEEKEND_WORK',          0, 'INFO',     false),
    ('HOLIDAY_WORK',          0, 'WARNING',  false),
    ('PATTERN_BREAK',         0, 'INFO',     false)
) AS et(exception_type, threshold_minutes, severity, auto_notify)
ON CONFLICT DO NOTHING;

-- M332: Attendance Exception Records
CREATE TABLE attendance.attendance_exception (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    work_date DATE NOT NULL,
    summary_id UUID,
    exception_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    threshold_minutes INT NOT NULL DEFAULT 0,
    actual_minutes INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    acknowledged_by VARCHAR(200),
    acknowledged_at TIMESTAMPTZ,
    resolved_by VARCHAR(200),
    resolved_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_att_exc_tenant_emp ON attendance.attendance_exception (tenant_id, employee_id);
CREATE INDEX idx_att_exc_work_date ON attendance.attendance_exception (work_date);
CREATE INDEX idx_att_exc_status ON attendance.attendance_exception (status);
CREATE INDEX idx_att_exc_type ON attendance.attendance_exception (exception_type);

-- M333: Device Master
CREATE TABLE attendance.device_master (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    device_type VARCHAR(30) NOT NULL DEFAULT 'TURNSTILE',
    location_id UUID,
    ip_address VARCHAR(50),
    serial_number VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_device_code UNIQUE (tenant_id, code)
);
CREATE INDEX idx_device_tenant ON attendance.device_master (tenant_id);

-- M333: Link device to turnstile imports
ALTER TABLE attendance.turnstile_import_batch
    ADD COLUMN IF NOT EXISTS device_id UUID REFERENCES attendance.device_master(id);
