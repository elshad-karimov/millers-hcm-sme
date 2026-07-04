-- M316: per-department clearance sign-off tracking (Offboarding PRD §12)
CREATE TABLE IF NOT EXISTS lifecycle.offboarding_clearance (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id           UUID         NOT NULL REFERENCES lifecycle.offboarding_case(id),
    department        VARCHAR(50)  NOT NULL,
    status            VARCHAR(40)  NOT NULL DEFAULT 'NOT_STARTED',
    cleared_by        VARCHAR(100),
    cleared_at        TIMESTAMPTZ,
    deduction_amount  NUMERIC(12,2),
    notes             TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_offboarding_clearance_dept UNIQUE (case_id, department)
);

CREATE INDEX IF NOT EXISTS idx_offboarding_clearance_case_id
    ON lifecycle.offboarding_clearance (case_id);
