-- HCM_13-17 quality gate — tenant_id backfill on the two remaining legacy tables
-- that predate the multi-tenant convention (GLOBAL RULE 2):
--   recruitment.job_posting (V146) and staffing.position (V4).
-- Single-tenant-safe default, same pattern as compbenefits V206 / performance V215.

ALTER TABLE recruitment.job_posting ADD COLUMN tenant_id varchar(64) NOT NULL DEFAULT 'default';
ALTER TABLE staffing.position       ADD COLUMN tenant_id varchar(64) NOT NULL DEFAULT 'default';

CREATE INDEX job_posting_tenant_idx ON recruitment.job_posting (tenant_id);
CREATE INDEX position_tenant_idx    ON staffing.position (tenant_id);
