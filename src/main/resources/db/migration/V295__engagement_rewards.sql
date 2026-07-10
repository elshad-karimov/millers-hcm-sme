-- M481: Reward catalog + points wallet + budgets + redemptions (engagement rewards)

CREATE SCHEMA IF NOT EXISTS engagement;

-- Reward catalog: redeemable items (points or monetary value)
CREATE TABLE engagement.reward_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    points INTEGER NOT NULL CHECK (points > 0),
    monetary_value NUMERIC(14,2) CHECK (monetary_value IS NULL OR monetary_value >= 0),
    category VARCHAR(60), -- e.g., GIFT_CARD, MERCHANDISE, EXPERIENCE, MONETARY, VOUCHER
    taxable BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(80),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(80),
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_reward_catalog_tenant_active ON engagement.reward_catalog(tenant_id, active);

-- Employee points wallet (one per employee per tenant)
CREATE TABLE engagement.reward_wallet (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL,
    employee_id UUID NOT NULL REFERENCES core_hr.employee(id) ON DELETE CASCADE,
    balance INTEGER NOT NULL DEFAULT 0 CHECK (balance >= 0),
    lifetime_earned INTEGER NOT NULL DEFAULT 0,
    lifetime_spent INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, employee_id)
);

CREATE INDEX idx_reward_wallet_tenant_employee ON engagement.reward_wallet(tenant_id, employee_id);

-- Reward budgets (annual allocation per org unit or global)
CREATE TABLE engagement.reward_budget (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL,
    year INTEGER NOT NULL,
    org_unit_id UUID REFERENCES organization.org_unit(id) ON DELETE CASCADE,
    total_amount NUMERIC(14,2) NOT NULL CHECK (total_amount >= 0),
    used_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (used_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(80),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(80),
    UNIQUE (tenant_id, year, org_unit_id)
);

CREATE INDEX idx_reward_budget_tenant_year ON engagement.reward_budget(tenant_id, year);
CREATE INDEX idx_reward_budget_org_unit ON engagement.reward_budget(org_unit_id) WHERE org_unit_id IS NOT NULL;

-- Redemption requests
CREATE TABLE engagement.reward_redemption (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL,
    redemption_no VARCHAR(20) NOT NULL UNIQUE,
    employee_id UUID NOT NULL REFERENCES core_hr.employee(id) ON DELETE CASCADE,
    catalog_item_id UUID NOT NULL REFERENCES engagement.reward_catalog(id),
    points INTEGER NOT NULL CHECK (points > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    fulfilled_at TIMESTAMPTZ,
    fulfilled_by VARCHAR(80),
    payroll_bonus_id UUID, -- FK to payroll.payroll_bonus if monetary (no FK constraint; bonus deletion is controlled elsewhere)
    delivery_address TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status IN ('REQUESTED', 'APPROVED', 'FULFILLED', 'REJECTED', 'CANCELLED'))
);

CREATE SEQUENCE engagement.redemption_seq START 1;

CREATE INDEX idx_redemption_tenant_status ON engagement.reward_redemption(tenant_id, status);
CREATE INDEX idx_redemption_employee ON engagement.reward_redemption(employee_id);
CREATE INDEX idx_redemption_catalog_item ON engagement.reward_redemption(catalog_item_id);

COMMENT ON TABLE engagement.reward_catalog IS 'M481: Redeemable reward catalog items';
COMMENT ON TABLE engagement.reward_wallet IS 'M481: Employee points wallet (one per employee per tenant)';
COMMENT ON TABLE engagement.reward_budget IS 'M481: Annual reward budgets by org unit';
COMMENT ON TABLE engagement.reward_redemption IS 'M481: Employee redemption requests';
