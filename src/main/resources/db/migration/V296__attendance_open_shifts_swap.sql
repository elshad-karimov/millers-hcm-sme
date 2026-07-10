-- M482: Open shifts + shift swap requests

-- Open shifts: published shifts employees can claim
CREATE TABLE attendance.open_shift (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL,
    shift_id UUID NOT NULL REFERENCES attendance.shift(id),
    shift_date DATE NOT NULL,
    org_unit_id UUID REFERENCES organization.org_unit(id),
    slots INTEGER NOT NULL CHECK (slots > 0),
    filled INTEGER NOT NULL DEFAULT 0 CHECK (filled >= 0 AND filled <= slots),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(80),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(80),
    CHECK (status IN ('OPEN', 'FILLED', 'CLOSED'))
);

CREATE INDEX idx_open_shift_tenant_date ON attendance.open_shift(tenant_id, shift_date);
CREATE INDEX idx_open_shift_status ON attendance.open_shift(status) WHERE status = 'OPEN';
CREATE INDEX idx_open_shift_org_unit ON attendance.open_shift(org_unit_id) WHERE org_unit_id IS NOT NULL;

-- Shift swap requests: employee A wants to swap with employee B
CREATE TABLE attendance.shift_swap_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL,
    request_no VARCHAR(20) NOT NULL UNIQUE,
    roster_entry_id UUID NOT NULL REFERENCES attendance.roster_entry(id),
    from_employee_id UUID NOT NULL REFERENCES core_hr.employee(id),
    to_employee_id UUID NOT NULL REFERENCES core_hr.employee(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at TIMESTAMPTZ,
    approved_by VARCHAR(80),
    rejection_reason TEXT,
    notes TEXT,
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE SEQUENCE attendance.shift_swap_seq START 1;

CREATE INDEX idx_shift_swap_tenant_status ON attendance.shift_swap_request(tenant_id, status);
CREATE INDEX idx_shift_swap_from_employee ON attendance.shift_swap_request(from_employee_id);
CREATE INDEX idx_shift_swap_to_employee ON attendance.shift_swap_request(to_employee_id);
CREATE INDEX idx_shift_swap_roster_entry ON attendance.shift_swap_request(roster_entry_id);

COMMENT ON TABLE attendance.open_shift IS 'M482: Open shifts available for employee claims';
COMMENT ON TABLE attendance.shift_swap_request IS 'M482: Shift swap requests between employees';
