-- ----------------------------------------------------------------------------
-- M433 — HCM_22 MSS Phase E.2: tenant-level settings (manager_can_view_salary).
--
-- Generic tenant_setting table for app-wide config. manager_can_view_salary
-- default false — managers cannot see direct-report salaries unless enabled.
-- ----------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS config;

CREATE TABLE IF NOT EXISTS config.tenant_setting (
    tenant_id   varchar(80)  NOT NULL,
    key         varchar(100) NOT NULL,
    value       text,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  varchar(80),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  varchar(80),
    PRIMARY KEY (tenant_id, key)
);

COMMENT ON TABLE config.tenant_setting IS
    'M433 — Tenant-level settings. Generic key-value store. manager_can_view_salary default false.';
