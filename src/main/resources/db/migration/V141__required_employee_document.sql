-- ----------------------------------------------------------------------------
-- M262 — Phase F.5a: Required employee document (PRD §29).
--
-- Tracks which documents an employee is required to submit. Driven by
-- the M248 position-profile auto-grant — when a position's profile has
-- a REQUIRED_DOCUMENT item set, hiring an employee into that position
-- auto-creates a "must submit X by Y" record; revoking the grant ends
-- the requirement.
--
-- Document submission itself piggybacks on the existing M73 attachment
-- registry — once the employee uploads the file, HR (or a future
-- self-service flow) links the attachment id here and the requirement
-- flips to SATISFIED.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS core_hr.required_employee_document (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id       UUID         NOT NULL,

    /** ID_CARD / PASSPORT / DIPLOMA / NDA / BANK_LETTER / MEDICAL_CERT /
        BACKGROUND_CHECK / OTHER */
    document_type     VARCHAR(32)  NOT NULL,

    /** Human-readable name from the profile item label, e.g. "Driver's License". */
    label             VARCHAR(200) NOT NULL,

    required_by_date  DATE,        -- nullable: "submit when convenient"

    /** PENDING / SATISFIED / WAIVED / EXPIRED */
    status            VARCHAR(32)  NOT NULL DEFAULT 'PENDING',

    /** Once SATISFIED, the attachment id that was accepted. */
    attachment_id     UUID,
    satisfied_at      TIMESTAMPTZ,
    satisfied_by      VARCHAR(120),

    /** Where the row came from: PROFILE_GRANT (auto) / MANUAL. */
    source            VARCHAR(32)  NOT NULL DEFAULT 'MANUAL',
    source_grant_id   UUID,

    notes             TEXT,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(120),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by        VARCHAR(120),

    CONSTRAINT chk_required_doc_status
        CHECK (status IN ('PENDING','SATISFIED','WAIVED','EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_required_doc_employee
    ON core_hr.required_employee_document(employee_id);

-- Partial index over rows still needing action — drives the SPA's
-- "you owe HR these documents" widget without scanning fulfilled rows.
CREATE INDEX IF NOT EXISTS idx_required_doc_pending
    ON core_hr.required_employee_document(employee_id, required_by_date)
    WHERE status = 'PENDING';
