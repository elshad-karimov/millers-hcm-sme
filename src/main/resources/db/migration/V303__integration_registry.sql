-- ----------------------------------------------------------------------------
-- M492 — HCM_49 Integration registry.
--
-- Centralized registry of external integrations (webhooks, file feeds, API
-- connectors). Tracks config + execution logs. Credentials stored by reference
-- only (never plaintext secrets). endpoint_url encrypted via
-- EncryptedStringConverter. 90-day log purge job.
-- ----------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS integration;

CREATE TABLE IF NOT EXISTS integration.integration_config (
    id                  uuid         PRIMARY KEY,
    tenant_id           varchar(80)  NOT NULL DEFAULT 'default',
    code                varchar(80)  NOT NULL,
    name                varchar(240) NOT NULL,
    direction           varchar(10)  NOT NULL,
    type                varchar(20)  NOT NULL,
    -- Encrypted via EncryptedStringConverter (AES-256 like webhook_url).
    endpoint_url        varchar(500),
    -- Reference name only (e.g., "ERP-API-KEY" stored in vault/env).
    credentials_ref     varchar(200),
    enabled             boolean      NOT NULL DEFAULT true,
    last_run_at         timestamptz,
    last_status         varchar(20),
    notes               text,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    created_by          varchar(80),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    updated_by          varchar(80),

    CONSTRAINT integration_config_direction_check
        CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT integration_config_type_check
        CHECK (type IN ('WEBHOOK', 'FILE', 'API')),
    UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_integration_config_tenant
    ON integration.integration_config (tenant_id);
CREATE INDEX IF NOT EXISTS idx_integration_config_enabled
    ON integration.integration_config (enabled) WHERE enabled = true;

CREATE TABLE IF NOT EXISTS integration.integration_log (
    id                  uuid         PRIMARY KEY,
    tenant_id           varchar(80)  NOT NULL DEFAULT 'default',
    config_id           uuid         NOT NULL
                            REFERENCES integration.integration_config (id) ON DELETE CASCADE,
    run_at              timestamptz  NOT NULL DEFAULT now(),
    status              varchar(20)  NOT NULL,
    records_processed   integer      DEFAULT 0,
    error_message       varchar(1000),

    CONSTRAINT integration_log_status_check
        CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_integration_log_config
    ON integration.integration_log (config_id, run_at DESC);
CREATE INDEX IF NOT EXISTS idx_integration_log_run_at
    ON integration.integration_log (run_at DESC);

COMMENT ON TABLE integration.integration_config IS
    'M492 — Integration registry. Config for external integrations (webhooks/file/API). Secrets by reference only.';
COMMENT ON TABLE integration.integration_log IS
    'M492 — Integration execution log. Purged after 90 days.';
