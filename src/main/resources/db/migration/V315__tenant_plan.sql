-- ----------------------------------------------------------------------------
-- V315 — Tenant plan / edition (SME edition, Path A).
--
-- Adds the commercial edition a tenant is on. The plan decides which modules
-- the tenant may use at all; the existing `disabled_modules` tenant setting
-- stays as the tenant's own opt-out WITHIN its plan. Effective set =
-- plan modules − tenant-disabled, with self-service + platform-admin always on.
--
-- Enforcement is server-side (ModuleAccessFilter → 403), not just nav hiding.
--
-- Backfill: existing rows take the LITE default. This repository IS the SME
-- edition, so LITE is the intended baseline — including for the seeded
-- 'default' dev tenant. To run a tenant on the full product:
--   UPDATE config.tenant SET plan = 'ENTERPRISE' WHERE id = 'default';
--
-- A downgrade never deletes data: modules simply stop answering and the rows
-- stay put, so an upgrade restores access.
-- ----------------------------------------------------------------------------

ALTER TABLE config.tenant
    ADD COLUMN IF NOT EXISTS plan VARCHAR(20) NOT NULL DEFAULT 'LITE';

ALTER TABLE config.tenant
    DROP CONSTRAINT IF EXISTS ck_tenant_plan;

ALTER TABLE config.tenant
    ADD CONSTRAINT ck_tenant_plan CHECK (plan IN ('LITE', 'STANDARD', 'ENTERPRISE'));

COMMENT ON COLUMN config.tenant.plan IS
  'Commercial edition: LITE | STANDARD | ENTERPRISE. Drives module entitlement (ModuleAccessFilter) and plan limits (PlanLimitGate).';
