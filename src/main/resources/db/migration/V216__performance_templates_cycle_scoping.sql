-- HCM_12 Performance Management — M389 (Phase A.2)
-- Review templates + weighted sections (PRD §5.2 / §18.1) and cycle scoping/eligibility
-- (PRD §5.1 / §10.2). A template defines which sections a review form has and each
-- section's weight in the overall score (weights of scoring sections sum to 100).

CREATE TABLE performance.perf_review_template (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       varchar(64) NOT NULL DEFAULT 'default',
    template_code   varchar(40) NOT NULL,
    template_name   varchar(160) NOT NULL,
    description     text,
    -- Applicability (nullable = any; AND-ed, à la benefit_eligibility_rule)
    legal_entity_id uuid,
    department_id   uuid,
    grade_id        uuid,
    employee_type   varchar(40),
    active          boolean NOT NULL DEFAULT true,
    created_by      varchar(80),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT perf_template_code_unique UNIQUE (tenant_id, template_code)
);

CREATE TABLE performance.perf_template_section (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      varchar(64) NOT NULL DEFAULT 'default',
    template_id    uuid NOT NULL REFERENCES performance.perf_review_template (id) ON DELETE CASCADE,
    section_type   varchar(40) NOT NULL,
    section_order  int NOT NULL DEFAULT 0,
    title          varchar(160),
    -- Weight in the §18.2 overall score. Only SCORING sections (GOALS/KPI/COMPETENCY/VALUES)
    -- carry weight; informational sections use 0.
    weight_percent numeric(5,2) NOT NULL DEFAULT 0,
    required       boolean NOT NULL DEFAULT true,
    CONSTRAINT perf_section_type_check CHECK (section_type IN (
        'GOALS','KPI','OKR','COMPETENCY','VALUES','BEHAVIORAL',
        'MANAGER_COMMENTS','EMPLOYEE_COMMENTS','DEVELOPMENT_PLAN','FINAL_RATING',
        'PROMOTION_RECOMMENDATION','COMPENSATION_RECOMMENDATION','SUMMARY','SIGNATURE')),
    CONSTRAINT perf_section_weight_nonneg CHECK (weight_percent >= 0)
);
CREATE INDEX perf_template_section_tpl_idx ON performance.perf_template_section (template_id, section_order);

-- ── Cycle scoping / eligibility (PRD §5.1 / §10.2) + scale/template links ─────
ALTER TABLE performance.review_cycle
    ADD COLUMN rating_scale_id    uuid REFERENCES performance.rating_scale (id),
    ADD COLUMN template_id        uuid REFERENCES performance.perf_review_template (id),
    ADD COLUMN legal_entity_id    uuid,
    ADD COLUMN department_id      uuid,
    ADD COLUMN grade_id           uuid,
    ADD COLUMN employee_type      varchar(40),
    ADD COLUMN min_service_months int;

-- Which template a review form was generated from (frozen at launch).
ALTER TABLE performance.performance_review
    ADD COLUMN template_id uuid REFERENCES performance.perf_review_template (id);

COMMENT ON TABLE performance.perf_review_template IS
    'HCM_12 M389 — review template: which sections a review form has + section weights (PRD §5.2).';

-- Seed a default template mirroring the gap-check weights: Goals 50 / KPI 25 / Competency 20 / Values 5.
INSERT INTO performance.perf_review_template (id, tenant_id, template_code, template_name, description)
VALUES ('ac000000-0000-0000-0000-000000000002', 'default', 'DEFAULT_ANNUAL', 'Default Annual Review',
        'Standard template — Goals 50%, KPIs 25%, Competencies 20%, Values 5% (PRD §18.1 example).');

INSERT INTO performance.perf_template_section (tenant_id, template_id, section_type, section_order, title, weight_percent, required)
VALUES
    ('default', 'ac000000-0000-0000-0000-000000000002', 'GOALS',             1, 'Goals',                50, true),
    ('default', 'ac000000-0000-0000-0000-000000000002', 'KPI',               2, 'KPIs',                 25, true),
    ('default', 'ac000000-0000-0000-0000-000000000002', 'COMPETENCY',        3, 'Competencies',         20, true),
    ('default', 'ac000000-0000-0000-0000-000000000002', 'VALUES',            4, 'Company Values',        5, true),
    ('default', 'ac000000-0000-0000-0000-000000000002', 'EMPLOYEE_COMMENTS', 5, 'Employee Comments',     0, false),
    ('default', 'ac000000-0000-0000-0000-000000000002', 'MANAGER_COMMENTS',  6, 'Manager Comments',      0, true),
    ('default', 'ac000000-0000-0000-0000-000000000002', 'DEVELOPMENT_PLAN',  7, 'Development Plan',      0, false),
    ('default', 'ac000000-0000-0000-0000-000000000002', 'FINAL_RATING',      8, 'Final Rating',          0, true);
