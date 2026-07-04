-- M140 — Legal Entity (§2 audit closure).
--
-- The audit found legal entity as a first-class master is entirely
-- absent: org_unit holds COMPANY at the top of the hierarchy but
-- never carried the registered-company fields a multi-entity HCM
-- needs (tax id, statutory reg numbers, payroll bank, fiscal calendar,
-- legal representative, currency).
--
-- This migration adds the master + an optional FK from org_unit so a
-- single Millers HCM deployment can host several legal entities and
-- their org sub-trees. Nullable on org_unit for backward compatibility
-- — existing rows continue to render without a legal-entity link.

CREATE TABLE IF NOT EXISTS organization.legal_entity (
    id                          UUID         PRIMARY KEY,
    code                        VARCHAR(60)  NOT NULL UNIQUE,
    name                        VARCHAR(240) NOT NULL,
    -- Registration with the local commercial / business registry.
    registration_number         VARCHAR(80),
    -- Tax identification number — drives statutory filings + payroll.
    tax_id                      VARCHAR(80),
    -- Social-insurance registration (DSMF in AZ; varies by jurisdiction).
    social_insurance_reg_number VARCHAR(80),
    -- Free-form legal address. Structured address (country/city/etc.) is
    -- already captured for HQ via the linked org_unit + future Location
    -- master (M141).
    legal_address               VARCHAR(500),
    -- ISO 3166-1 alpha-2 (same convention as employee.nationality).
    country                     VARCHAR(2),
    -- ISO 4217 alpha-3 (USD / EUR / AZN / …). Drives payroll currency.
    currency                    VARCHAR(3),
    -- Fiscal year window — free-form for now (e.g. "JAN-DEC", "APR-MAR").
    -- A future calendar master could replace this with a FK.
    fiscal_calendar             VARCHAR(40),

    -- Payroll bank account — used by the bank file generator. AES-256
    -- encryption converted at the JPA layer (same pattern as
    -- employee.bank_account / national_id).
    payroll_bank_name           VARCHAR(160),
    payroll_bank_account        VARCHAR(500),
    payroll_bank_swift          VARCHAR(11),

    -- Chart-of-accounts hook — opaque string pointer; finance owns the
    -- master and the HCM consumes the code without dictating shape.
    default_cost_centre_code    VARCHAR(60),
    chart_of_accounts_ref       VARCHAR(120),

    legal_representative_name   VARCHAR(160),
    legal_representative_title  VARCHAR(120),
    -- MinIO URL of the company seal PNG (printed on letters etc.).
    company_seal_url            VARCHAR(500),

    active                      BOOLEAN      NOT NULL DEFAULT TRUE,
    effective_from              DATE,
    effective_to                DATE,
    notes                       TEXT,

    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                  VARCHAR(80),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by                  VARCHAR(80),

    CONSTRAINT chk_le_country  CHECK (country IS NULL
        OR (length(country) = 2  AND country = upper(country))),
    CONSTRAINT chk_le_currency CHECK (currency IS NULL
        OR (length(currency) = 3 AND currency = upper(currency))),
    CONSTRAINT chk_le_effective_window
        CHECK (effective_to IS NULL OR effective_from IS NULL
            OR effective_to >= effective_from)
);

CREATE INDEX IF NOT EXISTS idx_legal_entity_active
    ON organization.legal_entity (active);

-- ── Link from org_unit ────────────────────────────────────────────────
-- Nullable on purpose so legacy versions render unchanged. The service
-- enforces propagation: when a COMPANY-level org_unit is linked to a
-- legal entity, descendants inherit unless they override.

ALTER TABLE organization.org_unit
    ADD COLUMN IF NOT EXISTS legal_entity_id UUID
        REFERENCES organization.legal_entity (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_org_unit_legal_entity
    ON organization.org_unit (legal_entity_id)
    WHERE legal_entity_id IS NOT NULL;
