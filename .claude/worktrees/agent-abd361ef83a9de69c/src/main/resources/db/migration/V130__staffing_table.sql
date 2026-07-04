-- ----------------------------------------------------------------------------
-- M245 — Staffing table / Ştat cədvəli (Phase C of Position Management spec).
--
-- A *staffing table* is the formal, government-grade snapshot of an
-- organization's approved establishment: which positions exist, how many
-- of each, what grade, what salary, what monthly salary fund. It is
-- per-legal-entity, versioned, and follows its own approval lifecycle.
-- In Azerbaijan it's commonly known as "ştat cədvəli" — a legally
-- required document for many entities.
--
-- Key design points:
--
--   - Header (`staffing_table`) carries the version, effective window,
--     status (DRAFT → PENDING_APPROVAL → ACTIVE → ARCHIVED), approver
--     and notes.
--
--   - Lines (`staffing_table_line`) snapshot the establishment — one
--     row per approved position group. Each line CAN link back to a
--     live position via {position_id, position_code} for tooling, but
--     the row is intentionally a snapshot — editing a position does
--     not retroactively change an APPROVED staffing table.
--
--   - Only one ACTIVE staffing table per legal entity per moment in
--     time. Enforced at the service layer because the constraint is
--     temporal (date range overlap), not just a unique key.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS staffing.staffing_table (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_entity_id UUID NOT NULL REFERENCES organization.legal_entity(id),
    version_code    VARCHAR(64)  NOT NULL,                   -- e.g. "2026-Q1", "2026-revised-v2"
    title           VARCHAR(200),                            -- "Ştat cədvəli 2026 Q1"
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    notes           TEXT,
    -- Approval breadcrumbs
    submitted_by    VARCHAR(120),
    submitted_at    TIMESTAMPTZ,
    approved_by     VARCHAR(120),
    approved_at     TIMESTAMPTZ,
    rejected_by     VARCHAR(120),
    rejected_at     TIMESTAMPTZ,
    reject_reason   TEXT,
    archived_at     TIMESTAMPTZ,
    -- Audit
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(120),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_staffing_table_legal_entity
    ON staffing.staffing_table(legal_entity_id, effective_from DESC);
CREATE INDEX IF NOT EXISTS idx_staffing_table_status
    ON staffing.staffing_table(status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_staffing_table_version
    ON staffing.staffing_table(legal_entity_id, version_code);

CREATE TABLE IF NOT EXISTS staffing.staffing_table_line (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staffing_table_id   UUID NOT NULL REFERENCES staffing.staffing_table(id) ON DELETE CASCADE,
    line_no             INT  NOT NULL,
    -- Structural unit (kept as both id + label so the snapshot survives
    -- org-unit renames).
    org_unit_id         UUID,
    org_unit_label      VARCHAR(200),
    -- Optional back-link to the live position. Kept so we can later
    -- run a "is the staffing table still consistent with the live
    -- positions?" health check.
    position_id         UUID,
    position_code       VARCHAR(64),
    position_title      VARCHAR(200) NOT NULL,
    grade               VARCHAR(32),
    -- Number of approved staff units for this line ("ştat vahidi sayı").
    approved_headcount  INT NOT NULL DEFAULT 1 CHECK (approved_headcount >= 0),
    -- Monthly rate per seat.
    monthly_salary      NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (monthly_salary >= 0),
    -- Monthly salary fund = approved_headcount × monthly_salary.
    -- Stored (not generated) for portability + so import/edits can
    -- override the computed value if needed.
    monthly_salary_fund NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (monthly_salary_fund >= 0),
    currency            VARCHAR(3) NOT NULL DEFAULT 'AZN',
    notes               TEXT
);

CREATE INDEX IF NOT EXISTS idx_staffing_table_line_table
    ON staffing.staffing_table_line(staffing_table_id, line_no);
CREATE INDEX IF NOT EXISTS idx_staffing_table_line_org_unit
    ON staffing.staffing_table_line(org_unit_id);
CREATE INDEX IF NOT EXISTS idx_staffing_table_line_position
    ON staffing.staffing_table_line(position_id);
