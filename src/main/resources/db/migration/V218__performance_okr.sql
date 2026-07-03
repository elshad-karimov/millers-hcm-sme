-- HCM_12 Performance Management — M391 (Phase A.4)
-- OKR: objectives (6 levels, parent alignment link §8.3), key results with
-- baseline/target/current + weight + confidence (§5.6 / §8.2), check-in history.
-- Objective progress = weighted average of KR progress (simple average when all
-- weights are 0). KR progress = (current − baseline) / (target − baseline) × 100,
-- clamped to 0..100 (BOOLEAN measurement → 0 or 100).

CREATE TABLE performance.okr_objective (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         varchar(64) NOT NULL DEFAULT 'default',
    title             varchar(300) NOT NULL,
    description       text,
    okr_level         varchar(20) NOT NULL,
    parent_id         uuid REFERENCES performance.okr_objective (id),
    owner_employee_id uuid,
    org_unit_id       uuid,
    legal_entity_id   uuid,
    cycle_id          uuid REFERENCES performance.review_cycle (id),
    period_start      date,
    period_end        date,
    due_date          date,
    status            varchar(20) NOT NULL DEFAULT 'ACTIVE',
    progress_percent  numeric(5,2) NOT NULL DEFAULT 0,
    confidence        varchar(10),
    created_by        varchar(80),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT okr_level_check CHECK (okr_level IN
        ('COMPANY','LEGAL_ENTITY','BUSINESS_UNIT','DEPARTMENT','TEAM','INDIVIDUAL')),
    CONSTRAINT okr_status_check CHECK (status IN ('DRAFT','ACTIVE','CLOSED','CANCELLED')),
    CONSTRAINT okr_confidence_check CHECK (confidence IS NULL OR confidence IN ('HIGH','MEDIUM','LOW'))
);
CREATE INDEX okr_objective_cycle_idx  ON performance.okr_objective (tenant_id, cycle_id);
CREATE INDEX okr_objective_parent_idx ON performance.okr_objective (parent_id);
CREATE INDEX okr_objective_owner_idx  ON performance.okr_objective (tenant_id, owner_employee_id);

CREATE TABLE performance.okr_key_result (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         varchar(64) NOT NULL DEFAULT 'default',
    objective_id      uuid NOT NULL REFERENCES performance.okr_objective (id) ON DELETE CASCADE,
    title             varchar(300) NOT NULL,
    measurement_type  varchar(20) NOT NULL DEFAULT 'NUMBER',
    baseline_value    numeric(14,2) NOT NULL DEFAULT 0,
    target_value      numeric(14,2) NOT NULL,
    current_value     numeric(14,2),
    progress_percent  numeric(5,2) NOT NULL DEFAULT 0,
    weight_percent    numeric(5,2) NOT NULL DEFAULT 0,
    confidence        varchar(10),
    owner_employee_id uuid,
    due_date          date,
    status            varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT okr_kr_measurement_check CHECK (measurement_type IN
        ('NUMBER','PERCENT','CURRENCY','BOOLEAN')),
    CONSTRAINT okr_kr_status_check CHECK (status IN ('ACTIVE','DONE','AT_RISK','CANCELLED')),
    CONSTRAINT okr_kr_confidence_check CHECK (confidence IS NULL OR confidence IN ('HIGH','MEDIUM','LOW'))
);
CREATE INDEX okr_key_result_objective_idx ON performance.okr_key_result (objective_id);

CREATE TABLE performance.okr_check_in (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     varchar(64) NOT NULL DEFAULT 'default',
    objective_id  uuid NOT NULL REFERENCES performance.okr_objective (id) ON DELETE CASCADE,
    key_result_id uuid REFERENCES performance.okr_key_result (id) ON DELETE CASCADE,
    old_value     numeric(14,2),
    new_value     numeric(14,2),
    confidence    varchar(10),
    comment       varchar(1000),
    recorded_by   varchar(80),
    recorded_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX okr_check_in_objective_idx ON performance.okr_check_in (objective_id);

COMMENT ON TABLE performance.okr_objective  IS 'HCM_12 M391 — OKR objectives, 6 levels, parent link = alignment (PRD §5.6/§8.3).';
COMMENT ON TABLE performance.okr_key_result IS 'HCM_12 M391 — key results; progress = (current−baseline)/(target−baseline)×100 clamped (PRD §8.2).';
COMMENT ON TABLE performance.okr_check_in   IS 'HCM_12 M391 — check-in comments + value-update history (PRD §8.1/§8.2).';
