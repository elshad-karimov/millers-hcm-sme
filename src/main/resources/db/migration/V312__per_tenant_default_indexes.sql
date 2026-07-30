-- ----------------------------------------------------------------------------
-- V312 — Make "single default" partial unique indexes per-tenant (Phase 5).
--
-- leave_group and payroll_group each enforce "at most one default" via a partial
-- unique index on (is_default) WHERE is_default — which is tenant-blind, so a
-- second tenant cloning its own default row collides. Scope the uniqueness to
-- the tenant: at most one default PER tenant.
-- ----------------------------------------------------------------------------

DROP INDEX IF EXISTS leave_mgmt.uq_leave_group_default;
CREATE UNIQUE INDEX uq_leave_group_default
    ON leave_mgmt.leave_group (tenant_id) WHERE is_default;

DROP INDEX IF EXISTS payroll.uq_payroll_group_default;
CREATE UNIQUE INDEX uq_payroll_group_default
    ON payroll.payroll_group (tenant_id) WHERE is_default;
