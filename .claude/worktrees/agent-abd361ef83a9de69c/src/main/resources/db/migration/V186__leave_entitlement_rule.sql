-- M339: Leave entitlement rules engine (Leave PRD §4.2 / §8.5)
-- Allows HR to define entitlement overrides keyed on employment_type + tenure window.
-- The accrual engine consults these rules (highest priority) before seniority brackets.

CREATE TABLE IF NOT EXISTS leave_mgmt.leave_entitlement_rule (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    leave_type_id           UUID NOT NULL REFERENCES leave_mgmt.leave_type(id),

    -- Null = matches any employment type
    employment_type         VARCHAR(20),

    -- Tenure window in completed months since hire_date (null = open bound)
    min_tenure_months       INT CHECK (min_tenure_months >= 0),
    max_tenure_months       INT CHECK (max_tenure_months >= 0),

    annual_entitlement_days NUMERIC(5,2) NOT NULL CHECK (annual_entitlement_days >= 0),

    -- Higher priority wins when multiple rules match
    priority                INT NOT NULL DEFAULT 0,
    active                  BOOLEAN NOT NULL DEFAULT TRUE,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100),

    CONSTRAINT chk_entitlement_tenure_order
        CHECK (min_tenure_months IS NULL OR max_tenure_months IS NULL
               OR min_tenure_months <= max_tenure_months)
);

CREATE INDEX IF NOT EXISTS idx_leave_entitlement_rule_type_active
    ON leave_mgmt.leave_entitlement_rule (leave_type_id, active, priority DESC);
