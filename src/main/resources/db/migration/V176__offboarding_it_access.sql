CREATE TABLE IF NOT EXISTS lifecycle.offboarding_it_access (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id       UUID         NOT NULL REFERENCES lifecycle.offboarding_case(id),
    access_type   VARCHAR(50)  NOT NULL,
    display_label VARCHAR(120) NOT NULL,
    access_status VARCHAR(40)  NOT NULL DEFAULT 'PENDING',
    removal_date  DATE,
    handled_by    VARCHAR(100),
    reference     VARCHAR(200),
    notes         TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_offboarding_it_access UNIQUE (case_id, access_type)
);
CREATE INDEX IF NOT EXISTS idx_offboarding_it_access_case_id
    ON lifecycle.offboarding_it_access (case_id);
