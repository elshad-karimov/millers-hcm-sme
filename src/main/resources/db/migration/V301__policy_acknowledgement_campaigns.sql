-- ----------------------------------------------------------------------------
-- M490 — HCM_42 Policy re-acknowledgement campaigns.
--
-- HR launches campaigns to notify employees to re-acknowledge a specific
-- policy version (e.g., updated Code of Conduct). Campaign audience can be
-- ALL employees or filtered by department (org_unit). Progress tracked by
-- counting acknowledgements from the target audience.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS policy.acknowledgement_campaign (
    id              uuid         PRIMARY KEY,
    tenant_id       varchar(80)  NOT NULL DEFAULT 'default',
    policy_id       uuid         NOT NULL
                        REFERENCES policy.policy_document (id) ON DELETE CASCADE,
    policy_version  integer      NOT NULL,
    name            varchar(240) NOT NULL,
    audience        varchar(20)  NOT NULL DEFAULT 'ALL',
    -- When audience='DEPARTMENT', this FK points to the org_unit.
    audience_ref    uuid,
    due_date        date         NOT NULL DEFAULT (CURRENT_DATE + INTERVAL '14 days'),
    status          varchar(20)  NOT NULL DEFAULT 'DRAFT',
    launched_at     timestamptz,
    launched_by     varchar(80),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      varchar(80),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      varchar(80),

    CONSTRAINT ack_campaign_audience_check CHECK (audience IN ('ALL', 'DEPARTMENT')),
    CONSTRAINT ack_campaign_status_check CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_ack_campaign_tenant
    ON policy.acknowledgement_campaign (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ack_campaign_policy
    ON policy.acknowledgement_campaign (policy_id, policy_version);
CREATE INDEX IF NOT EXISTS idx_ack_campaign_status
    ON policy.acknowledgement_campaign (status);

COMMENT ON TABLE policy.acknowledgement_campaign IS
    'M490 — Policy re-acknowledgement campaigns. HR notifies audience to re-ack a policy version.';
