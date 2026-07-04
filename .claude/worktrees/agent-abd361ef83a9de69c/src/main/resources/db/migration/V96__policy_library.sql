-- M138 — Company policy library.
--
-- The audit found §27 ESS "view company policies" was missing entirely.
-- HR needs a place to publish versioned policy documents; employees
-- need to browse them on self-service and acknowledge the ones tagged
-- "requires acknowledgement".
--
-- Schema sits in its own `policy` namespace alongside other
-- module-scoped schemas (leave_mgmt, performance, lifecycle, …).

CREATE SCHEMA IF NOT EXISTS policy;

-- ── policy.policy_document ────────────────────────────────────────────────
--
-- Each row is one version. New versions of the same policy share `code`
-- but get bumped `version` + a new id; the controller forbids editing
-- a published row so history is immutable.
CREATE TABLE IF NOT EXISTS policy.policy_document (
    id              UUID         PRIMARY KEY,
    -- Stable cross-version identifier (e.g. "CODE-OF-CONDUCT").
    code            VARCHAR(80)  NOT NULL,
    title           VARCHAR(240) NOT NULL,
    -- One-line summary shown in the directory listing.
    summary         VARCHAR(500),
    category        VARCHAR(40)  NOT NULL,
    -- Monotonic per code; service enforces +1 on each new draft.
    version         INTEGER      NOT NULL DEFAULT 1,
    body_format     VARCHAR(20)  NOT NULL,
    -- Either body_text (MARKDOWN / HTML) OR attachment_url (PDF) is
    -- populated, never both. Enforced application-side.
    body_text       TEXT,
    attachment_url  VARCHAR(500),
    effective_from  DATE,
    effective_to    DATE,
    -- DRAFT → PUBLISHED → ARCHIVED. Lock published rows: only the
    -- `status` field can change after publish.
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    -- When TRUE, the SPA shows a sticky "acknowledge" prompt until the
    -- employee has acked this version.
    requires_ack    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(80),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      VARCHAR(80),

    CONSTRAINT chk_pd_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    CONSTRAINT chk_pd_body_format CHECK (body_format IN ('MARKDOWN','HTML','PDF','URL')),
    -- Effective end can be null (open-ended) but not before start.
    CONSTRAINT chk_pd_effective_window
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    -- One row per (code, version) — the service generates the version
    -- monotonically, this is the database backstop.
    UNIQUE (code, version)
);

CREATE INDEX IF NOT EXISTS idx_pd_status_category
    ON policy.policy_document (status, category);
CREATE INDEX IF NOT EXISTS idx_pd_code
    ON policy.policy_document (code, version);

-- ── policy.acknowledgement ────────────────────────────────────────────────
--
-- One row per (policy_id, employee_id) — an employee can acknowledge a
-- given version exactly once. When a new version is published the SPA
-- treats unacknowledged versions as "fresh" because the FK points to
-- the row id, not the code.
CREATE TABLE IF NOT EXISTS policy.acknowledgement (
    id                 UUID         PRIMARY KEY,
    policy_id          UUID         NOT NULL
                        REFERENCES policy.policy_document (id) ON DELETE CASCADE,
    employee_id        UUID         NOT NULL
                        REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    -- Snapshotted at ack time so audit reports can re-render which
    -- version the employee saw, even after later versions ship.
    version_acknowledged INTEGER    NOT NULL,
    acknowledged_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    acknowledged_ip    VARCHAR(64),

    UNIQUE (policy_id, employee_id)
);

CREATE INDEX IF NOT EXISTS idx_pa_employee
    ON policy.acknowledgement (employee_id, acknowledged_at DESC);
