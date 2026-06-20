CREATE TABLE IF NOT EXISTS lifecycle.offboarding_asset_return (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id             UUID         NOT NULL REFERENCES lifecycle.offboarding_case(id),
    employee_asset_id   UUID         REFERENCES core_hr.employee_asset(id),
    asset_type          VARCHAR(40)  NOT NULL,
    asset_name          VARCHAR(200) NOT NULL,
    asset_identifier    VARCHAR(120),
    return_status       VARCHAR(40)  NOT NULL DEFAULT 'PENDING_RETURN',
    return_date         DATE,
    condition_at_return VARCHAR(40),
    deduction_amount    NUMERIC(12,2),
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_offboarding_asset_employee_asset UNIQUE (case_id, employee_asset_id)
);
CREATE INDEX IF NOT EXISTS idx_offboarding_asset_return_case_id
    ON lifecycle.offboarding_asset_return (case_id);
