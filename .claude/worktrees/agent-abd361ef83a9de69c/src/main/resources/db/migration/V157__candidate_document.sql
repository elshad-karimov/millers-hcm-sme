-- ----------------------------------------------------------------------------
-- M292 — Recruitment PRD §12: candidate document checklist.
--
-- A configurable master catalog of required documents (document_type) and a
-- per-candidate checklist (candidate_document) tracking each one through
-- PENDING → RECEIVED → VERIFIED (or REJECTED / WAIVED). Files themselves
-- live in MinIO via the M16 attachment registry, scoped to the document id
-- (owner_module=RECRUITMENT, owner_entity=candidatedocument) — no
-- cross-schema FK here.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS recruitment.document_type (
    id               UUID PRIMARY KEY,
    code             VARCHAR(40)  NOT NULL UNIQUE,
    name             VARCHAR(160) NOT NULL,
    description      VARCHAR(500),
    default_required BOOLEAN      NOT NULL DEFAULT FALSE,
    ordinal          INT          NOT NULL DEFAULT 0,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       VARCHAR(120),
    updated_by       VARCHAR(120)
);

-- Sensible AZ-recruitment defaults; HR can edit / add / deactivate.
INSERT INTO recruitment.document_type (id, code, name, description, default_required, ordinal)
VALUES
    ('d0c70001-0000-0000-0000-000000000001', 'CV',             'CV / Resume',                 'Curriculum vitae',                          TRUE,  10),
    ('d0c70001-0000-0000-0000-000000000002', 'ID',             'ID / Passport',               'Şəxsiyyət vəsiqəsi or passport copy',       TRUE,  20),
    ('d0c70001-0000-0000-0000-000000000003', 'DIPLOMA',        'Education diploma',           'Highest-degree diploma + transcript',       TRUE,  30),
    ('d0c70001-0000-0000-0000-000000000004', 'WORK_BOOK',      'Work book',                   'Əmək kitabçası (employment record book)',   FALSE, 40),
    ('d0c70001-0000-0000-0000-000000000005', 'REFERENCE',      'Reference letter',            'Reference / recommendation letter',         FALSE, 50),
    ('d0c70001-0000-0000-0000-000000000006', 'MILITARY',       'Military service document',   'Military service / registration card',      FALSE, 60),
    ('d0c70001-0000-0000-0000-000000000007', 'MEDICAL',        'Medical certificate',         'Pre-employment health certificate',         FALSE, 70),
    ('d0c70001-0000-0000-0000-000000000008', 'CRIMINAL_RECORD','Criminal-record certificate', 'Police clearance / arayış',                 FALSE, 80),
    ('d0c70001-0000-0000-0000-000000000009', 'WORK_PERMIT',    'Work permit',                 'Work / residence permit (foreign nationals)', FALSE, 90),
    ('d0c70001-0000-0000-0000-00000000000a', 'PHOTO',          'Photograph',                  'Passport-size photograph',                  FALSE, 100)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS recruitment.candidate_document (
    id               UUID PRIMARY KEY,
    candidate_id     UUID NOT NULL
                         REFERENCES recruitment.candidate(id) ON DELETE CASCADE,
    document_type_id UUID NOT NULL
                         REFERENCES recruitment.document_type(id),
    required         BOOLEAN     NOT NULL DEFAULT TRUE,
    status           VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    notes            VARCHAR(500),
    received_at      TIMESTAMPTZ,
    verified_at      TIMESTAMPTZ,
    verified_by      VARCHAR(120),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_candidate_document UNIQUE (candidate_id, document_type_id),
    CONSTRAINT chk_candidate_document_status
        CHECK (status IN ('PENDING', 'RECEIVED', 'VERIFIED', 'REJECTED', 'WAIVED'))
);

CREATE INDEX IF NOT EXISTS idx_candidate_document_candidate
    ON recruitment.candidate_document (candidate_id);
