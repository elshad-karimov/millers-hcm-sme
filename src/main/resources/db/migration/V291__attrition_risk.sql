-- M476 — Attrition risk heuristic scoring

CREATE TABLE analytics.attrition_risk (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(100) NOT NULL DEFAULT 'default',
    employee_id     UUID NOT NULL REFERENCES core_hr.employee(id),
    score           INT NOT NULL CHECK (score >= 0 AND score <= 100),
    factors         VARCHAR(500), -- comma-separated list of factors applied
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, employee_id)
);

CREATE INDEX idx_attrition_risk_tenant_score ON analytics.attrition_risk(tenant_id, score DESC);
CREATE INDEX idx_attrition_risk_employee ON analytics.attrition_risk(employee_id);
