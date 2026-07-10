-- M487: International assignments (tracking only)

CREATE SCHEMA IF NOT EXISTS mobility;

-- International assignments
CREATE TABLE mobility.international_assignment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL,
    assignment_no VARCHAR(20) NOT NULL UNIQUE,
    employee_id UUID NOT NULL REFERENCES core_hr.employee(id),
    host_country VARCHAR(80) NOT NULL,
    host_city VARCHAR(120),
    host_entity VARCHAR(200),
    purpose TEXT,
    start_date DATE NOT NULL,
    end_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    visa_type VARCHAR(60),
    visa_expiry DATE,
    housing_allowance NUMERIC(14,2),
    cola_amount NUMERIC(14,2),
    hardship_amount NUMERIC(14,2),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(80),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(80),
    CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE SEQUENCE mobility.assignment_seq START 1;

CREATE INDEX idx_ia_tenant_status ON mobility.international_assignment(tenant_id, status);
CREATE INDEX idx_ia_employee ON mobility.international_assignment(employee_id);
CREATE INDEX idx_ia_visa_expiry ON mobility.international_assignment(visa_expiry) WHERE visa_expiry IS NOT NULL;

COMMENT ON TABLE mobility.international_assignment IS 'M487: International assignments (tracking only)';
COMMENT ON COLUMN mobility.international_assignment.housing_allowance IS 'Manual tracking field';
COMMENT ON COLUMN mobility.international_assignment.cola_amount IS 'Cost of living adjustment (manual)';
COMMENT ON COLUMN mobility.international_assignment.hardship_amount IS 'Hardship allowance (manual)';
