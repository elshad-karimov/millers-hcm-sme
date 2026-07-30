-- ----------------------------------------------------------------------------
-- V310 — Tenant registry (multi-tenancy Phase 3).
--
-- The `config.tenant` master table is the single source of truth that maps a
-- Keycloak realm's JWT issuer (`iss` claim) to an internal tenant id. It drives:
--   * the multi-issuer resource-server resolver (which realms to trust), and
--   * the TenantResolutionFilter (iss -> tenantId -> TenantContext).
--
-- This is a SYSTEM table: it is NOT itself tenant-scoped (it is the list *of*
-- tenants), so it carries no tenant_id discriminator / @TenantId.
--
-- Seed row: the existing single tenant 'default', bound to the current dev
-- realm's issuer (KC_HOSTNAME_URL = http://localhost:5180). Production overrides
-- the issuer via UPDATE or the provisioning API.
-- ----------------------------------------------------------------------------

CREATE TABLE config.tenant (
    id          VARCHAR(64)  PRIMARY KEY,             -- internal tenant id (== the discriminator value)
    name        VARCHAR(200) NOT NULL,
    issuer_uri  VARCHAR(500) NOT NULL,                -- JWT `iss` this tenant's tokens carry
    realm       VARCHAR(200),                         -- Keycloak realm name (informational)
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(80),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(80),
    CONSTRAINT uq_tenant_issuer UNIQUE (issuer_uri)
);

COMMENT ON TABLE config.tenant IS
  'Multi-tenancy registry: maps a Keycloak realm JWT issuer to an internal tenant id. System table — not tenant-scoped.';

-- Seed the incumbent tenant. issuer_uri matches spring.security.oauth2
-- .resourceserver.jwt.issuer-uri (dev: http://localhost:5180/realms/millers-hcm).
INSERT INTO config.tenant (id, name, issuer_uri, realm, active, created_by)
VALUES ('default', 'Millers HCM (default)',
        'http://localhost:5180/realms/millers-hcm', 'millers-hcm', TRUE, 'system')
ON CONFLICT (id) DO NOTHING;
