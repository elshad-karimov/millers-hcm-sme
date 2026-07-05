-- V273: Mileage claims (M453 — HCM_28 Phase F.2)
-- Employee mileage claims for business use of personal/company vehicles.
-- PRD §28.3.7 & analysis.md Phase F.

CREATE SEQUENCE business_trip.mileage_claim_no_seq START 1;

CREATE TABLE business_trip.mileage_claim (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL DEFAULT 'default',

    claim_no VARCHAR(20) NOT NULL UNIQUE,
    employee_id UUID NOT NULL,  -- FK to core_hr.employee
    claim_date DATE NOT NULL,

    vehicle_type VARCHAR(20) NOT NULL,  -- PERSONAL_CAR | COMPANY_CAR | MOTORBIKE
    start_location VARCHAR(200) NOT NULL,
    end_location VARCHAR(200) NOT NULL,
    distance_km NUMERIC(8,1) NOT NULL,
    rate_per_km NUMERIC(6,2) NOT NULL DEFAULT 0.30,  -- AZN per km, tenant-configurable seam

    total_amount NUMERIC(10,2) NOT NULL,  -- distance × rate, computed in service
    currency VARCHAR(3) NOT NULL DEFAULT 'AZN',

    status VARCHAR(20) NOT NULL,  -- DRAFT | SUBMITTED | APPROVED | REJECTED | PAID
    approved_by VARCHAR(120),
    approved_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    rejection_reason TEXT,
    paid_at TIMESTAMPTZ,

    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120)
);

CREATE INDEX idx_mileage_claim_employee ON business_trip.mileage_claim(tenant_id, employee_id);
CREATE INDEX idx_mileage_claim_status ON business_trip.mileage_claim(tenant_id, status);
COMMENT ON TABLE business_trip.mileage_claim IS 'Employee mileage claims for business travel (M453)';
COMMENT ON COLUMN business_trip.mileage_claim.rate_per_km IS 'Default 0.30 AZN/km; tenant-configurable seam';
