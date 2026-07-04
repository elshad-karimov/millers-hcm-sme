-- HCM_15 M410 — EMPLOYEE talent pools (PRD §15.5/§15.6).
-- Unlike recruitment.talent_pool (M87 — candidate CRM), this is for
-- EMPLOYEE grouping: HiPo cohorts, succession bench, mobility registry,
-- leadership pipeline. HR-only visibility (GLOBAL RULE 17).

CREATE TABLE IF NOT EXISTS performance.employee_talent_pool (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    code            VARCHAR(30) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    purpose         VARCHAR(1000),
    owner_employee_id UUID,  -- HR analyst responsible; optional
    active          BOOLEAN NOT NULL DEFAULT true,
    created_by      VARCHAR(80),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_emp_pool_code UNIQUE (tenant_id, code)
);
CREATE INDEX idx_emp_pool_tenant ON performance.employee_talent_pool (tenant_id, active);

CREATE TABLE IF NOT EXISTS performance.employee_pool_member (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    pool_id         UUID NOT NULL REFERENCES performance.employee_talent_pool (id) ON DELETE CASCADE,
    employee_id     UUID NOT NULL,
    added_by        VARCHAR(80),
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    note            VARCHAR(500),
    CONSTRAINT uq_emp_pool_member UNIQUE (pool_id, employee_id)
);
CREATE INDEX idx_emp_pool_member_tenant ON performance.employee_pool_member (tenant_id);
CREATE INDEX idx_emp_pool_member_emp ON performance.employee_pool_member (employee_id);

COMMENT ON TABLE performance.employee_talent_pool IS 'HCM_15 M410 — employee talent pools (PRD §15.5)';
COMMENT ON TABLE performance.employee_pool_member IS 'HCM_15 M410 — talent pool membership (PRD §15.6)';
