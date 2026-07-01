-- M365 + M366: Incentive + Commission plans + payouts

-- M365: Incentive plans
CREATE TABLE compensation.incentive_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    measure VARCHAR(20) NOT NULL,
    target_pct NUMERIC(6,2) NOT NULL,
    threshold_achievement NUMERIC(6,2) DEFAULT 80,
    target_achievement NUMERIC(6,2) DEFAULT 100,
    cap_achievement NUMERIC(6,2) DEFAULT 120,
    max_payout_pct NUMERIC(6,2),
    currency VARCHAR(3) DEFAULT 'AZN',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, code)
);

CREATE TABLE compensation.incentive_payout (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    plan_id UUID NOT NULL REFERENCES compensation.incentive_plan(id),
    employee_id UUID NOT NULL REFERENCES core_hr.employee(id),
    period VARCHAR(20) NOT NULL,
    eligible_salary NUMERIC(14,2) NOT NULL,
    achievement_pct NUMERIC(6,2) NOT NULL,
    payout_pct NUMERIC(6,2) NOT NULL,
    payout_amount NUMERIC(14,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','APPROVED','PAID','CANCELLED')),
    payroll_bonus_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by VARCHAR(100),
    approved_at TIMESTAMPTZ
);

CREATE INDEX idx_incentive_payout_tenant_plan_status
    ON compensation.incentive_payout(tenant_id, plan_id, status);

-- M366: Commission plans
CREATE TABLE compensation.commission_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    basis VARCHAR(20) NOT NULL,
    flat_rate_pct NUMERIC(6,2),
    tiered BOOLEAN DEFAULT false,
    currency VARCHAR(3) DEFAULT 'AZN',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, code)
);

CREATE TABLE compensation.commission_tier (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    plan_id UUID NOT NULL REFERENCES compensation.commission_plan(id) ON DELETE CASCADE,
    from_amount NUMERIC(14,2) NOT NULL,
    to_amount NUMERIC(14,2),
    rate_pct NUMERIC(6,2) NOT NULL,
    sort_order INT NOT NULL
);

CREATE TABLE compensation.commission_payout (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    plan_id UUID NOT NULL REFERENCES compensation.commission_plan(id),
    employee_id UUID NOT NULL REFERENCES core_hr.employee(id),
    period VARCHAR(20) NOT NULL,
    sales_amount NUMERIC(14,2) NOT NULL,
    commission_amount NUMERIC(14,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','APPROVED','PAID','CANCELLED')),
    payroll_bonus_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by VARCHAR(100),
    approved_at TIMESTAMPTZ
);

CREATE INDEX idx_commission_payout_tenant_plan_status
    ON compensation.commission_payout(tenant_id, plan_id, status);
