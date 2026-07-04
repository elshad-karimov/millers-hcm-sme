-- M322: Benefits closure tracking + rehire eligibility (Offboarding PRD §23/§34)
ALTER TABLE lifecycle.offboarding_case
    ADD COLUMN IF NOT EXISTS benefits_closure_status  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS benefits_closure_notes   TEXT,
    ADD COLUMN IF NOT EXISTS benefits_closed_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rehire_eligible          BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS rehire_eligible_from     DATE,
    ADD COLUMN IF NOT EXISTS rehire_ineligible_reason TEXT;

CREATE TABLE IF NOT EXISTS lifecycle.offboarding_benefits_closure (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id          UUID         NOT NULL REFERENCES lifecycle.offboarding_case(id),
    benefit_type     VARCHAR(60)  NOT NULL,
    description      VARCHAR(200),
    closure_status   VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    closed_date      DATE,
    provider         VARCHAR(120),
    reference        VARCHAR(120),
    notes            TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_offboarding_benefits_closure UNIQUE (case_id, benefit_type)
);

CREATE INDEX IF NOT EXISTS idx_offboarding_benefits_closure_case_id
    ON lifecycle.offboarding_benefits_closure (case_id);
