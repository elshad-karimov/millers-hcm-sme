-- HCM_12 Performance Management — M388 (Phase A.1)
-- 1) tenant_id backfill: the performance schema (V19/V46/V70/V82) predates the multi-tenant
--    convention (PRD §3.1 / GLOBAL RULE 2). Single-tenant-safe default, same as compbenefits V206.
-- 2) Rating-scale master (PRD §5.3 + §18.3): tenant-configurable scales replace the JSONB
--    rating map embedded on review_cycle (kept for back-compat; new work reads the master).

-- ── tenant_id backfill on all performance tables ─────────────────────────────
ALTER TABLE performance.review_cycle             ADD COLUMN tenant_id varchar(64) NOT NULL DEFAULT 'default';
ALTER TABLE performance.goal                     ADD COLUMN tenant_id varchar(64) NOT NULL DEFAULT 'default';
ALTER TABLE performance.performance_review       ADD COLUMN tenant_id varchar(64) NOT NULL DEFAULT 'default';
ALTER TABLE performance.feedback                 ADD COLUMN tenant_id varchar(64) NOT NULL DEFAULT 'default';
ALTER TABLE performance.calibration_session     ADD COLUMN tenant_id varchar(64) NOT NULL DEFAULT 'default';
ALTER TABLE performance.cycle_calibration_target ADD COLUMN tenant_id varchar(64) NOT NULL DEFAULT 'default';
ALTER TABLE performance.calibration_edit_log     ADD COLUMN tenant_id varchar(64) NOT NULL DEFAULT 'default';
ALTER TABLE performance.succession_nomination    ADD COLUMN tenant_id varchar(64) NOT NULL DEFAULT 'default';

CREATE INDEX review_cycle_tenant_idx        ON performance.review_cycle (tenant_id);
CREATE INDEX goal_tenant_idx                ON performance.goal (tenant_id);
CREATE INDEX performance_review_tenant_idx  ON performance.performance_review (tenant_id);
CREATE INDEX feedback_tenant_idx            ON performance.feedback (tenant_id);

-- ── Rating scale master (PRD §5.3) ───────────────────────────────────────────
CREATE TABLE performance.rating_scale (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   varchar(64) NOT NULL DEFAULT 'default',
    scale_code  varchar(40) NOT NULL,
    scale_name  varchar(160) NOT NULL,
    scale_type  varchar(30) NOT NULL DEFAULT 'NUMERIC_1_5',
    description text,
    active      boolean NOT NULL DEFAULT true,
    is_default  boolean NOT NULL DEFAULT false,
    created_by  varchar(80),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT rating_scale_code_unique UNIQUE (tenant_id, scale_code),
    CONSTRAINT rating_scale_type_check
        CHECK (scale_type IN ('NUMERIC_1_5','NUMERIC_1_10','PERCENTAGE','DESCRIPTIVE','LETTER','PASS_FAIL'))
);

CREATE TABLE performance.rating_scale_value (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      varchar(64) NOT NULL DEFAULT 'default',
    scale_id       uuid NOT NULL REFERENCES performance.rating_scale (id) ON DELETE CASCADE,
    value_order    int NOT NULL,
    rating_value   numeric(6,2) NOT NULL,
    rating_label   varchar(120) NOT NULL,
    description    varchar(300),
    -- Score band for numeric→label conversion (PRD §18.3); nullable when unused.
    min_percentage numeric(5,2),
    max_percentage numeric(5,2),
    color_code     varchar(20),
    CONSTRAINT rating_scale_value_order_unique UNIQUE (scale_id, value_order)
);
CREATE INDEX rating_scale_value_scale_idx ON performance.rating_scale_value (scale_id);

COMMENT ON TABLE performance.rating_scale IS
    'HCM_12 M388 — tenant-configurable rating scale master (PRD §5.3).';

-- Seed the AZ default 5-point scale with §18.3 score bands (gap-check defaults).
INSERT INTO performance.rating_scale (id, tenant_id, scale_code, scale_name, scale_type, description, is_default)
VALUES ('ac000000-0000-0000-0000-000000000001', 'default', 'DEFAULT_5PT', 'Default 5-Point Scale',
        'NUMERIC_1_5', 'Standard 5-point performance scale (PRD §5.3 example).', true);

INSERT INTO performance.rating_scale_value
    (tenant_id, scale_id, value_order, rating_value, rating_label, description, min_percentage, max_percentage, color_code)
VALUES
    ('default', 'ac000000-0000-0000-0000-000000000001', 1, 1, 'Unsatisfactory',        'Does not meet expectations',        0,  39.99, 'red'),
    ('default', 'ac000000-0000-0000-0000-000000000001', 2, 2, 'Needs Improvement',     'Partially meets expectations',     40,  59.99, 'orange'),
    ('default', 'ac000000-0000-0000-0000-000000000001', 3, 3, 'Meets Expectations',    'Fully meets expectations',         60,  74.99, 'blue'),
    ('default', 'ac000000-0000-0000-0000-000000000001', 4, 4, 'Exceeds Expectations',  'Performs above expectations',      75,  89.99, 'green'),
    ('default', 'ac000000-0000-0000-0000-000000000001', 5, 5, 'Outstanding',           'Exceptional performance',          90, 100,    'gold');
