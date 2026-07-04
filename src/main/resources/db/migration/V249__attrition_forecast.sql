-- M423: Attrition forecast

CREATE TABLE staffing.attrition_forecast (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    plan_id UUID,
    org_unit_id UUID,
    forecast_date DATE NOT NULL,
    expected_exits NUMERIC(6,2) NOT NULL DEFAULT 0,
    basis VARCHAR(50) NOT NULL,
    detail VARCHAR(500),

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_attrition_forecast_plan
        FOREIGN KEY (plan_id) REFERENCES staffing.workforce_plan(id) ON DELETE CASCADE,
    CONSTRAINT fk_attrition_forecast_org_unit
        FOREIGN KEY (org_unit_id) REFERENCES organization.org_unit(id)
);

CREATE INDEX idx_attrition_forecast_plan ON staffing.attrition_forecast(plan_id, tenant_id);
CREATE INDEX idx_attrition_forecast_org_unit ON staffing.attrition_forecast(org_unit_id, tenant_id);

COMMENT ON TABLE staffing.attrition_forecast IS 'M423: Projected attrition by org-unit';
COMMENT ON COLUMN staffing.attrition_forecast.basis IS 'HISTORICAL/CONTRACT_EXPIRY/RETIREMENT/RISK';
