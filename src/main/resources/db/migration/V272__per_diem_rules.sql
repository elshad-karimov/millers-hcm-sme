-- V272: Per-diem allowance rules (M452 — HCM_28 Phase F.1)
-- Multi-tenant per-diem calculation by destination, grade, and trip type.
-- PRD §28.3.4 & analysis.md Phase F seam.

CREATE TABLE business_trip.per_diem_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL DEFAULT 'default',

    -- Matching dimensions (most specific wins)
    destination_country VARCHAR(80) NOT NULL,
    destination_city VARCHAR(120),  -- NULL = country-wide rule
    employee_grade VARCHAR(40),     -- NULL = all grades
    trip_type VARCHAR(30),          -- DOMESTIC/INTERNATIONAL/LOCAL — NULL = all types

    -- Allowances per day
    meal_allowance NUMERIC(10,2) NOT NULL,
    lodging_allowance NUMERIC(10,2) NOT NULL,
    incidentals NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'AZN',

    -- Validity window
    effective_from DATE NOT NULL,
    effective_to DATE,

    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_per_diem_tenant_country ON business_trip.per_diem_rule(tenant_id, destination_country);
CREATE INDEX idx_per_diem_active ON business_trip.per_diem_rule(tenant_id, active) WHERE active;
COMMENT ON TABLE business_trip.per_diem_rule IS 'Per-diem allowance rules for business trips (M452)';

-- Seed AZ per-diem defaults (PRD analysis.md Phase F).
INSERT INTO business_trip.per_diem_rule
    (destination_country, destination_city, employee_grade, trip_type, meal_allowance, lodging_allowance, incidentals, effective_from)
VALUES
    -- Azerbaijan (Baku higher than regions)
    ('Azerbaijan', 'Baku', NULL, NULL, 30.00, 25.00, 5.00, '2026-01-01'),
    ('Azerbaijan', NULL,   NULL, NULL, 25.00, 20.00, 5.00, '2026-01-01'),  -- regions

    -- International destinations
    ('Turkey',     'Istanbul', NULL, NULL, 40.00, 35.00, 5.00, '2026-01-01'),
    ('UAE',        'Dubai',    NULL, NULL, 50.00, 40.00, 10.00, '2026-01-01'),
    ('Russia',     'Moscow',   NULL, NULL, 45.00, 35.00, 10.00, '2026-01-01');

COMMENT ON COLUMN business_trip.per_diem_rule.destination_city IS 'NULL = country-wide rule; city-specific rule takes precedence';
COMMENT ON COLUMN business_trip.per_diem_rule.employee_grade IS 'NULL = all grades; grade-specific rule takes precedence';
COMMENT ON COLUMN business_trip.per_diem_rule.trip_type IS 'NULL = all trip types; type-specific rule takes precedence';
