-- HCM_11 Benefits Administration — M373 (Phase A.1)
-- Benefit categories master + tenant_id backfill onto the pre-multitenant M108 tables.
--
-- M108 (V73) shipped a flat benefit_type enum. The full PRD needs a tenant-configurable
-- category master (PRD §2): Health, Life, Pension, Meal, Transport, Housing, Mobile, Wellness.
-- The category master becomes the source of truth going forward; benefit_type stays on
-- benefit_plan for back-compat and plans link to a category in M375.

CREATE TABLE compbenefits.benefit_category (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         varchar(64) NOT NULL DEFAULT 'default',
    code              varchar(40) NOT NULL,
    name              varchar(160) NOT NULL,
    description       text,
    -- Whether the employer-paid portion is a taxable benefit-in-kind (AZ: cash-like
    -- allowances such as meal/transport/housing are taxable; insured plans generally not).
    taxable           boolean NOT NULL DEFAULT false,
    -- Whether plans in this category require an external provider/vendor (M374).
    requires_provider boolean NOT NULL DEFAULT false,
    display_order     int NOT NULL DEFAULT 0,
    active            boolean NOT NULL DEFAULT true,
    created_by        varchar(80),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT benefit_category_code_unique UNIQUE (tenant_id, code)
);

CREATE INDEX benefit_category_active_idx
    ON compbenefits.benefit_category (tenant_id, active, display_order);

COMMENT ON TABLE compbenefits.benefit_category IS
    'HCM_11 M373 — Tenant-configurable benefit category master (PRD §2).';

-- Seed the 8 AZ default categories (tenant 'default').
INSERT INTO compbenefits.benefit_category
    (tenant_id, code, name, description, taxable, requires_provider, display_order)
VALUES
    ('default', 'HEALTH',    'Health Insurance',       'Medical / health insurance coverage.',              false, true,  1),
    ('default', 'LIFE',      'Life Insurance',         'Group life and accidental death coverage.',         false, true,  2),
    ('default', 'PENSION',   'Pension / Retirement',   'Voluntary pension and retirement savings.',         false, true,  3),
    ('default', 'MEAL',      'Meal Allowance',         'Meal or food-card benefit.',                        true,  false, 4),
    ('default', 'TRANSPORT', 'Transport Allowance',    'Commute / transport benefit.',                      true,  false, 5),
    ('default', 'HOUSING',   'Housing Allowance',      'Housing / accommodation benefit.',                  true,  false, 6),
    ('default', 'MOBILE',    'Mobile / Communication', 'Mobile phone and communication benefit.',           true,  false, 7),
    ('default', 'WELLNESS',  'Wellness Program',       'Gym, wellness and employee-assistance programs.',   false, false, 8);

-- ── tenant_id backfill on the pre-multitenant M108 tables ────────────────────
-- V73 predates the tenant_id convention; add the column (single-tenant safe default).
ALTER TABLE compbenefits.benefit_plan
    ADD COLUMN tenant_id varchar(64) NOT NULL DEFAULT 'default';
ALTER TABLE compbenefits.benefit_enrollment
    ADD COLUMN tenant_id varchar(64) NOT NULL DEFAULT 'default';

CREATE INDEX benefit_plan_tenant_idx      ON compbenefits.benefit_plan (tenant_id);
CREATE INDEX benefit_enrollment_tenant_idx ON compbenefits.benefit_enrollment (tenant_id);
