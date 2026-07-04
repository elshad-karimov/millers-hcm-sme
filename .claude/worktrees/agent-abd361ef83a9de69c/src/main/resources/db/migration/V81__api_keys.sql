-- M120 — API keys + per-key rate limiting.
--
-- Keys are issued for machine-to-machine integrations (HRIS sync, payroll
-- exports, BI extracts). The plaintext key is shown exactly once at
-- creation; the database stores only its SHA-256 hash so a leaked DB
-- dump can't be used to mint requests.
--
-- last4 carries the last four characters of the plaintext so admins can
-- identify a key in the list ("hcm_…a3F1") without storing the key itself.
--
-- scopes_json is a JSONB array of role names that this key is allowed to
-- authenticate as — same names the JWT layer maps via realm_access.roles.
-- A key cannot grant scopes its owner doesn't already have.

CREATE SCHEMA IF NOT EXISTS security;

CREATE TABLE security.api_key (
    id                     UUID PRIMARY KEY,
    label                  VARCHAR(160) NOT NULL,
    description            TEXT,
    owner_user             VARCHAR(160) NOT NULL,

    -- SHA-256 of the plaintext key (hex). Unique so two issued keys can
    -- never collide; an attacker who finds a DB row still can't authenticate.
    key_hash               CHAR(64)     NOT NULL UNIQUE,
    last4                  VARCHAR(4)   NOT NULL,

    scopes_json            JSONB        NOT NULL,
    rate_limit_per_min     INTEGER      NOT NULL DEFAULT 60,

    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at             TIMESTAMPTZ,
    last_used_at           TIMESTAMPTZ,
    last_used_ip           VARCHAR(64),
    usage_count            BIGINT       NOT NULL DEFAULT 0,

    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             VARCHAR(160),
    revoked_at             TIMESTAMPTZ,
    revoked_by             VARCHAR(160),
    revoke_reason          TEXT,

    CONSTRAINT chk_api_key_rate_limit CHECK (rate_limit_per_min BETWEEN 1 AND 10000)
);

CREATE INDEX ix_api_key_owner   ON security.api_key (owner_user);
CREATE INDEX ix_api_key_active  ON security.api_key (active) WHERE active = TRUE;

-- Append-only per-(key, minute) usage rollup used to drive admin-facing
-- "requests in the last hour" widgets without scanning a real request log.
-- The auth filter UPSERTs into this each time a key is used.
CREATE TABLE security.api_key_usage_minute (
    api_key_id     UUID         NOT NULL REFERENCES security.api_key (id) ON DELETE CASCADE,
    minute_bucket  TIMESTAMPTZ  NOT NULL,
    request_count  INTEGER      NOT NULL DEFAULT 1,
    rejected_count INTEGER      NOT NULL DEFAULT 0,
    PRIMARY KEY (api_key_id, minute_bucket)
);

CREATE INDEX ix_api_key_usage_recent
    ON security.api_key_usage_minute (api_key_id, minute_bucket DESC);
