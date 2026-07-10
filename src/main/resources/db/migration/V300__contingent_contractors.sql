-- M488: Contractor engagements

CREATE SCHEMA IF NOT EXISTS contingent;

-- Contractor engagement (over Employee with employment_type=CONTRACTOR)
CREATE TABLE contingent.contractor_engagement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL,
    employee_id UUID NOT NULL REFERENCES core_hr.employee(id) ON DELETE CASCADE,
    vendor_agency_id UUID, -- Optional FK to recruitment.agency (if exists)
    contract_start DATE NOT NULL,
    contract_end DATE,
    rate NUMERIC(14,2),
    rate_unit VARCHAR(20),
    po_number VARCHAR(60),
    tenure_alert_days INTEGER NOT NULL DEFAULT 30,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    conversion_date DATE,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(80),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(80),
    UNIQUE (tenant_id, employee_id),
    CHECK (status IN ('ACTIVE', 'ENDED', 'CONVERTED')),
    CHECK (rate_unit IS NULL OR rate_unit IN ('HOURLY', 'DAILY', 'MONTHLY')),
    CHECK (contract_end IS NULL OR contract_end >= contract_start)
);

CREATE INDEX idx_contractor_tenant_status ON contingent.contractor_engagement(tenant_id, status);
CREATE INDEX idx_contractor_employee ON contingent.contractor_engagement(employee_id);
CREATE INDEX idx_contractor_contract_end ON contingent.contractor_engagement(contract_end) WHERE contract_end IS NOT NULL;

COMMENT ON TABLE contingent.contractor_engagement IS 'M488: Contractor engagement details';
COMMENT ON COLUMN contingent.contractor_engagement.rate IS 'Finance/HR confidential';
COMMENT ON COLUMN contingent.contractor_engagement.po_number IS 'Finance/HR confidential';
COMMENT ON COLUMN contingent.contractor_engagement.conversion_date IS 'M489: Set when converted to FTE';
