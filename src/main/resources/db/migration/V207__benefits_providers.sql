-- HCM_11 Benefits Administration — M374 (Phase A.2)
-- Benefit provider / vendor master. M108's benefit_plan.provider is free-text only;
-- this adds a proper vendor master (insurer / pension fund / clinic / vendor) with
-- contract tracking, and links plans to it (legacy free-text provider column kept).

CREATE TABLE compbenefits.benefit_provider (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      varchar(64) NOT NULL DEFAULT 'default',
    code           varchar(40) NOT NULL,
    name           varchar(200) NOT NULL,
    provider_type  varchar(30) NOT NULL,
    contact_name   varchar(160),
    contact_email  varchar(200),
    contact_phone  varchar(60),
    website        varchar(200),
    contract_no    varchar(80),
    contract_start date,
    contract_end   date,
    notes          text,
    active         boolean NOT NULL DEFAULT true,
    created_by     varchar(80),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT benefit_provider_code_unique UNIQUE (tenant_id, code),
    CONSTRAINT benefit_provider_type_check
        CHECK (provider_type IN ('INSURER','PENSION_FUND','CLINIC','VENDOR','BANK','OTHER')),
    CONSTRAINT benefit_provider_contract_window
        CHECK (contract_end IS NULL OR contract_start IS NULL OR contract_end >= contract_start)
);

CREATE INDEX benefit_provider_active_idx ON compbenefits.benefit_provider (tenant_id, active);

COMMENT ON TABLE compbenefits.benefit_provider IS
    'HCM_11 M374 — Benefit provider / vendor master (insurers, pension funds, clinics).';

-- Link plans to the provider master (nullable; legacy free-text provider column stays).
ALTER TABLE compbenefits.benefit_plan
    ADD COLUMN provider_id uuid REFERENCES compbenefits.benefit_provider (id);
CREATE INDEX benefit_plan_provider_idx ON compbenefits.benefit_plan (provider_id);
