-- M157: Department / Annual / Compliance Training Plans (PRD §8.14.2).
--
-- A Training Plan groups one or more courses into a structured plan that can
-- be targeted at a whole department or position and activated to batch-enroll
-- all matching employees in one click.
--
-- Plan types: DEPARTMENT, ANNUAL, COMPLIANCE, CAREER_PATH
-- Plan status lifecycle: DRAFT → ACTIVE → COMPLETED | ARCHIVED

CREATE TABLE learning.training_plan (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_no             VARCHAR(20)  NOT NULL UNIQUE,     -- e.g. TP-00001
    name                VARCHAR(300) NOT NULL,
    description         TEXT,
    plan_type           VARCHAR(30)  NOT NULL DEFAULT 'DEPARTMENT'
                            CHECK (plan_type IN ('DEPARTMENT','ANNUAL','COMPLIANCE','CAREER_PATH')),
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                            CHECK (status IN ('DRAFT','ACTIVE','COMPLETED','ARCHIVED')),
    -- Scoping (at least one expected for DEPARTMENT plans)
    org_unit_id         UUID         REFERENCES organization.org_unit(id) ON DELETE SET NULL,
    fiscal_year         SMALLINT,                        -- e.g. 2026 for ANNUAL plans
    deadline            DATE,
    -- Ownership / metadata
    owner_id            UUID         REFERENCES core_hr.employee(id) ON DELETE SET NULL,
    created_by          VARCHAR(80),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    activated_at        TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    -- Enrollment summary (refreshed on enroll-all)
    enrolled_count      INT          NOT NULL DEFAULT 0,
    completed_count     INT          NOT NULL DEFAULT 0
);

CREATE SEQUENCE learning.training_plan_seq START 1 INCREMENT 1;

CREATE INDEX idx_tp_type   ON learning.training_plan (plan_type);
CREATE INDEX idx_tp_status ON learning.training_plan (status);
CREATE INDEX idx_tp_org    ON learning.training_plan (org_unit_id) WHERE org_unit_id IS NOT NULL;

-- Items: one course per row, with optional scoping override per item.
CREATE TABLE learning.training_plan_item (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id         UUID         NOT NULL REFERENCES learning.training_plan(id) ON DELETE CASCADE,
    course_id       UUID         NOT NULL REFERENCES learning.course(id),
    due_date        DATE,
    -- Optional override scope (NULL = inherit from plan)
    position_id     UUID         REFERENCES staffing.position(id) ON DELETE SET NULL,
    notes           TEXT,
    sort_order      INT          NOT NULL DEFAULT 0,
    UNIQUE (plan_id, course_id)
);

CREATE INDEX idx_tpi_plan ON learning.training_plan_item (plan_id);
