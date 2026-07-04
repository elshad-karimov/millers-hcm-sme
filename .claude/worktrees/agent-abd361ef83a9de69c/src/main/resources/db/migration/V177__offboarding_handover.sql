CREATE TABLE IF NOT EXISTS lifecycle.offboarding_handover_task (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id            UUID         NOT NULL REFERENCES lifecycle.offboarding_case(id),
    title              VARCHAR(200) NOT NULL,
    category           VARCHAR(50)  NOT NULL DEFAULT 'GENERAL',
    description        TEXT,
    handover_to        VARCHAR(150),
    handover_status    VARCHAR(40)  NOT NULL DEFAULT 'PENDING',
    due_date           DATE,
    completed_at       TIMESTAMPTZ,
    notes              TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_offboarding_handover_case_id
    ON lifecycle.offboarding_handover_task (case_id);

-- Add replacement_required flag to offboarding_case
ALTER TABLE lifecycle.offboarding_case
    ADD COLUMN IF NOT EXISTS replacement_required    BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS replacement_notes       TEXT,
    ADD COLUMN IF NOT EXISTS vacancy_triggered_at    TIMESTAMPTZ;
