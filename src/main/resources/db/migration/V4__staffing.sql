-- Staffing Table module (PRD Section 8.3).
-- Position planning with vacancy state, headcount, and budget linkage.

CREATE SEQUENCE staffing.position_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE staffing.position (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),

    code                VARCHAR(64)    NOT NULL UNIQUE,
    title               VARCHAR(200)   NOT NULL,

    -- Soft reference into organization.org_unit. Not a hard FK because org units
    -- belong to a specific structure_version; positions live across versions.
    org_unit_id         UUID,
    org_unit_label      VARCHAR(200),

    grade               VARCHAR(32),
    job_family          VARCHAR(64),
    job_level           VARCHAR(32),

    approved_headcount  INTEGER        NOT NULL DEFAULT 0,
    occupied_headcount  INTEGER        NOT NULL DEFAULT 0,

    salary_min          NUMERIC(14, 2),
    salary_max          NUMERIC(14, 2),
    currency            VARCHAR(3)     NOT NULL DEFAULT 'AZN',

    employment_type     VARCHAR(32),
    cost_centre         VARCHAR(64),
    budget_code         VARCHAR(64),
    location            VARCHAR(160),
    effective_from      DATE,
    effective_to        DATE,

    -- OCCUPIED, VACANT, PARTIALLY_OCCUPIED, FROZEN, PLANNED, CANCELLED  (PRD 8.3.2)
    vacancy_state       VARCHAR(32)    NOT NULL,
    -- Lifecycle of the position record itself.
    status              VARCHAR(32)    NOT NULL,

    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          VARCHAR(120),
    updated_by          VARCHAR(120),

    CONSTRAINT chk_position_headcount CHECK (occupied_headcount <= approved_headcount),
    CONSTRAINT chk_position_salary    CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max)
);

CREATE INDEX idx_position_org_unit  ON staffing.position (org_unit_id);
CREATE INDEX idx_position_vacancy   ON staffing.position (vacancy_state);
CREATE INDEX idx_position_status    ON staffing.position (status);
CREATE INDEX idx_position_cost_ctr  ON staffing.position (cost_centre);
