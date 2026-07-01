-- V202 — Merit matrix + compensation budget control (M363 + M364)

-- M363 — Merit matrix: auto-suggest merit % from performance × range position
CREATE TABLE compensation.merit_matrix (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    code VARCHAR(50) NOT NULL,
    name VARCHAR(160) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_merit_matrix_tenant ON compensation.merit_matrix(tenant_id);

CREATE TABLE compensation.merit_matrix_cell (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    matrix_id UUID NOT NULL REFERENCES compensation.merit_matrix(id) ON DELETE CASCADE,
    performance_band VARCHAR(20) NOT NULL,  -- EXCELLENT, GOOD, MEETS, BELOW
    range_position VARCHAR(10) NOT NULL,    -- LOW, MID, HIGH
    merit_pct NUMERIC(5,2) NOT NULL,
    UNIQUE (matrix_id, performance_band, range_position)
);

CREATE INDEX idx_merit_matrix_cell_matrix ON compensation.merit_matrix_cell(matrix_id);

-- Seed default merit matrix (PRD §11 example)
INSERT INTO compensation.merit_matrix (id, tenant_id, code, name, is_active, created_at)
VALUES (gen_random_uuid(), 'default', 'DEFAULT', 'Default Merit Matrix', true, NOW());

-- Insert cells (EXCELLENT 8/6/4, GOOD 5/4/2, MEETS 3/2/1, BELOW 0/0/0)
INSERT INTO compensation.merit_matrix_cell (id, tenant_id, matrix_id, performance_band, range_position, merit_pct)
SELECT gen_random_uuid(), 'default', m.id, band, pos, pct
FROM compensation.merit_matrix m
CROSS JOIN (
    VALUES
        ('EXCELLENT', 'LOW', 8.00),
        ('EXCELLENT', 'MID', 6.00),
        ('EXCELLENT', 'HIGH', 4.00),
        ('GOOD', 'LOW', 5.00),
        ('GOOD', 'MID', 4.00),
        ('GOOD', 'HIGH', 2.00),
        ('MEETS', 'LOW', 3.00),
        ('MEETS', 'MID', 2.00),
        ('MEETS', 'HIGH', 1.00),
        ('BELOW', 'LOW', 0.00),
        ('BELOW', 'MID', 0.00),
        ('BELOW', 'HIGH', 0.00)
) AS cells(band, pos, pct)
WHERE m.code = 'DEFAULT';

-- M364 — Compensation budget control
CREATE TABLE compensation.compensation_budget (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    cycle_id UUID,  -- nullable FK to compbenefits.comp_cycle
    scope_type VARCHAR(20) NOT NULL CHECK (scope_type IN ('GLOBAL','DEPARTMENT','MANAGER','GRADE','LEGAL_ENTITY')),
    scope_ref VARCHAR(100),  -- nullable for GLOBAL, otherwise dept/manager/grade ID
    budget_type VARCHAR(20) NOT NULL CHECK (budget_type IN ('MERIT','BONUS','INCENTIVE','PROMOTION')),
    amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'AZN',
    consumed_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    effective_from DATE,
    effective_to DATE,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_compensation_budget_tenant_type ON compensation.compensation_budget(tenant_id, budget_type);
CREATE INDEX idx_compensation_budget_cycle ON compensation.compensation_budget(cycle_id);
