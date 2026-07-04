-- HCM_13 Goals/OKR — M403 (Phase A, the only module-13 gap after HCM_12)
-- Business goal-type catalog (PRD 13 §4 — 14 types) + organisational anchors on
-- goals so company / legal-entity / business-unit / department goals can point at
-- the org structure instead of only an employee owner. Everything else in module
-- 13 (weighting, approval, cascade, scoring, OKR, KPI) shipped with HCM_12.

CREATE TABLE performance.goal_type (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        varchar(64) NOT NULL DEFAULT 'default',
    code             varchar(40) NOT NULL,
    name             varchar(120) NOT NULL,
    description      varchar(500),
    default_category varchar(24) NOT NULL DEFAULT 'INDIVIDUAL',
    sort_order       int NOT NULL DEFAULT 0,
    active           boolean NOT NULL DEFAULT true,
    CONSTRAINT goal_type_code_unique UNIQUE (tenant_id, code),
    CONSTRAINT goal_type_category_check CHECK (default_category IN
        ('COMPANY','DEPARTMENT','TEAM','INDIVIDUAL','DEVELOPMENT'))
);

INSERT INTO performance.goal_type (code, name, default_category, sort_order) VALUES
    ('COMPANY_GOAL',          'Company goal',           'COMPANY',      1),
    ('LEGAL_ENTITY_GOAL',     'Legal entity goal',      'COMPANY',      2),
    ('BUSINESS_UNIT_GOAL',    'Business unit goal',     'DEPARTMENT',   3),
    ('DEPARTMENT_GOAL',       'Department goal',        'DEPARTMENT',   4),
    ('TEAM_GOAL',             'Team goal',              'TEAM',         5),
    ('POSITION_GOAL',         'Position-based goal',    'INDIVIDUAL',   6),
    ('INDIVIDUAL_GOAL',       'Individual goal',        'INDIVIDUAL',   7),
    ('PROJECT_GOAL',          'Project goal',           'TEAM',         8),
    ('DEVELOPMENT_GOAL',      'Development goal',       'DEVELOPMENT',  9),
    ('SALES_GOAL',            'Sales goal',             'INDIVIDUAL',  10),
    ('OPERATIONAL_KPI',       'Operational KPI',        'INDIVIDUAL',  11),
    ('COMPLIANCE_GOAL',       'Compliance goal',        'INDIVIDUAL',  12),
    ('CUSTOMER_SERVICE_GOAL', 'Customer service goal',  'INDIVIDUAL',  13),
    ('FINANCIAL_GOAL',        'Financial goal',         'COMPANY',     14);

ALTER TABLE performance.goal
    ADD COLUMN goal_type_id    uuid REFERENCES performance.goal_type (id),
    ADD COLUMN org_unit_id     uuid,
    ADD COLUMN legal_entity_id uuid;

COMMENT ON TABLE performance.goal_type IS
    'HCM_13 M403 — business goal-type catalog (PRD 13 §4); default_category pre-selects the goal category.';
