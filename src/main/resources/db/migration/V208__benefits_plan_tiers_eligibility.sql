-- HCM_11 Benefits Administration — M375 (Phase A.3)
-- Plan enrichment: category + plan-year link on benefit_plan, tiered coverage levels,
-- and structured (AND-ed, OR-of-rules) eligibility rules per plan.

-- ── Plan → category + plan year ──────────────────────────────────────────────
ALTER TABLE compbenefits.benefit_plan
    ADD COLUMN category_id uuid REFERENCES compbenefits.benefit_category (id),
    ADD COLUMN plan_year   int;
CREATE INDEX benefit_plan_category_idx ON compbenefits.benefit_plan (category_id);

-- ── Coverage tiers (EMPLOYEE_ONLY / +SPOUSE / +CHILDREN / FAMILY / CUSTOM) ────
-- Per-tier employer/employee split and coverage amount. A plan with no tiers falls
-- back to its flat employer/employee contribution (M108 back-compat).
CREATE TABLE compbenefits.benefit_plan_tier (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             varchar(64) NOT NULL DEFAULT 'default',
    plan_id               uuid NOT NULL REFERENCES compbenefits.benefit_plan (id) ON DELETE CASCADE,
    tier_code             varchar(30) NOT NULL,
    tier_label            varchar(120),
    employer_contribution numeric(14,2) NOT NULL DEFAULT 0,
    employee_contribution numeric(14,2) NOT NULL DEFAULT 0,
    coverage_amount       numeric(14,2),
    display_order         int NOT NULL DEFAULT 0,
    active                boolean NOT NULL DEFAULT true,
    created_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT benefit_plan_tier_code_check
        CHECK (tier_code IN ('EMPLOYEE_ONLY','EMPLOYEE_SPOUSE','EMPLOYEE_CHILDREN','FAMILY','CUSTOM')),
    CONSTRAINT benefit_plan_tier_contrib_nonneg
        CHECK (employer_contribution >= 0 AND employee_contribution >= 0),
    CONSTRAINT benefit_plan_tier_unique UNIQUE (plan_id, tier_code)
);
CREATE INDEX benefit_plan_tier_plan_idx ON compbenefits.benefit_plan_tier (plan_id, active);

-- ── Eligibility rules ────────────────────────────────────────────────────────
-- Each row = a set of AND-ed conditions (a null column means "any"). A plan is
-- eligible for an employee if it has no rules (open to all) OR ANY active rule
-- matches. Mirrors the M298 ChecklistTemplateRule pattern; enforced at enrolment (M376).
CREATE TABLE compbenefits.benefit_eligibility_rule (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          varchar(64) NOT NULL DEFAULT 'default',
    plan_id            uuid NOT NULL REFERENCES compbenefits.benefit_plan (id) ON DELETE CASCADE,
    employment_type    varchar(40),
    department_id      uuid,
    org_unit_id        uuid,
    grade_id           uuid,
    employee_category  varchar(40),
    min_service_months int,
    description        varchar(200),
    active             boolean NOT NULL DEFAULT true,
    created_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT benefit_eligibility_min_service_nonneg
        CHECK (min_service_months IS NULL OR min_service_months >= 0)
);
CREATE INDEX benefit_eligibility_plan_idx ON compbenefits.benefit_eligibility_rule (plan_id, active);

COMMENT ON TABLE compbenefits.benefit_plan_tier IS
    'HCM_11 M375 — Coverage tiers per plan with per-tier contribution split.';
COMMENT ON TABLE compbenefits.benefit_eligibility_rule IS
    'HCM_11 M375 — Structured eligibility rules (AND-ed conditions, OR-of-rows) per plan.';
