-- M479 — Engagement action plans

CREATE TABLE engagement.engagement_action_plan (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(100) NOT NULL DEFAULT 'default',
    campaign_id     UUID REFERENCES engagement.survey_campaign(id), -- nullable
    org_unit_id     UUID, -- nullable, future FK when org_unit exists
    owner_username  VARCHAR(255) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT | ACTIVE | COMPLETED
    due_date        DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE engagement.engagement_action_item (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(100) NOT NULL DEFAULT 'default',
    plan_id         UUID NOT NULL REFERENCES engagement.engagement_action_plan(id) ON DELETE CASCADE,
    description     TEXT NOT NULL,
    responsible_username VARCHAR(255),
    done            BOOLEAN NOT NULL DEFAULT false,
    done_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_engagement_action_plan_tenant ON engagement.engagement_action_plan(tenant_id);
CREATE INDEX idx_engagement_action_plan_status ON engagement.engagement_action_plan(status);
CREATE INDEX idx_engagement_action_item_plan ON engagement.engagement_action_item(plan_id);
