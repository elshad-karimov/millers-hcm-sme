-- ----------------------------------------------------------------------------
-- M302 — Onboarding PRD Phase B.2: IT / identity provisioning (request-tracking).
--
-- Reuses the M301 onboarding_resource_request entity. IT_ACCOUNT_REQUEST and
-- SECURITY_ACCESS_TASK onboarding tasks now auto-spawn requests (IT_ACCOUNT /
-- ACCESS_CARD categories). Two new columns let IT classify and close them:
--   provisioning_kind — email / system access / license / access card / VPN
--   provisioned_ref   — what was actually set up (account, ticket, systems)
--
-- Request-tracking only (per scope): no live Keycloak/mailbox/license creation.
-- Plain VARCHAR (no CHECK) so the kind list can grow without a migration.
-- ----------------------------------------------------------------------------

ALTER TABLE lifecycle.onboarding_resource_request
    ADD COLUMN IF NOT EXISTS provisioning_kind varchar(24),
    ADD COLUMN IF NOT EXISTS provisioned_ref   varchar(300);

COMMENT ON COLUMN lifecycle.onboarding_resource_request.provisioning_kind IS
    'M302 — ItProvisioningKind: email/system/license/access-card/VPN bucket for IT requests.';
COMMENT ON COLUMN lifecycle.onboarding_resource_request.provisioned_ref IS
    'M302 — what IT set up at fulfilment (account name, ticket no, systems granted). Request-tracking only.';
