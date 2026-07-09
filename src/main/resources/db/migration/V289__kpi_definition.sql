-- M473 — KPI definition catalog for analytics dashboards

CREATE TABLE analytics.kpi_definition (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(100) NOT NULL DEFAULT 'default',
    code            VARCHAR(100) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    category        VARCHAR(50) NOT NULL, -- HEADCOUNT | TURNOVER | COST | COMPLIANCE | ENGAGEMENT | LEARNING
    description     TEXT,
    unit            VARCHAR(50), -- e.g., count, percentage, days, currency
    target_value    NUMERIC(15,2),
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_kpi_definition_tenant_active ON analytics.kpi_definition(tenant_id, active);

-- Seed ~10 standard KPIs
INSERT INTO analytics.kpi_definition (tenant_id, code, name, category, description, unit, target_value, active) VALUES
('default', 'HEADCOUNT_ACTIVE', 'Active Headcount', 'HEADCOUNT', 'Total active employees', 'count', NULL, true),
('default', 'TURNOVER_12M', '12-Month Turnover Rate', 'TURNOVER', 'Voluntary + involuntary turnover over 12 months', 'percentage', 10.0, true),
('default', 'ABSENCE_RATE', 'Absence Rate', 'TURNOVER', 'Unplanned absences as % of scheduled days', 'percentage', 3.0, true),
('default', 'AVG_TENURE_YRS', 'Average Tenure (Years)', 'HEADCOUNT', 'Mean years since hire date', 'years', NULL, true),
('default', 'TRAINING_COMPLETION', 'Training Completion Rate', 'LEARNING', 'Completed mandatory courses / total mandatory enrollments', 'percentage', 95.0, true),
('default', 'ENPS', 'Employee Net Promoter Score', 'ENGAGEMENT', 'eNPS from latest pulse survey', 'score', 30.0, true),
('default', 'OPEN_POSITIONS', 'Open Positions', 'HEADCOUNT', 'Active job postings with no hire', 'count', NULL, true),
('default', 'TIME_TO_HIRE_DAYS', 'Time to Hire (Days)', 'HEADCOUNT', 'Median days from posting to offer accepted', 'days', 30.0, true),
('default', 'PAYROLL_COST_MONTHLY', 'Monthly Payroll Cost', 'COST', 'Total payroll cost for current month', 'currency', NULL, true),
('default', 'COMPLIANCE_DEADLINES_MET', 'Compliance Deadlines Met', 'COMPLIANCE', 'Percentage of statutory deadlines met on time', 'percentage', 100.0, true);
