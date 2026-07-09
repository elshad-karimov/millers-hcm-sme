-- M474 — Saved dashboard layouts

CREATE TABLE analytics.dashboard_layout (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(100) NOT NULL DEFAULT 'default',
    owner_username  VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    shared          BOOLEAN NOT NULL DEFAULT false,
    widgets         JSONB NOT NULL, -- array of {kpiCode, position}
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dashboard_layout_tenant_owner ON analytics.dashboard_layout(tenant_id, owner_username);
CREATE INDEX idx_dashboard_layout_tenant_shared ON analytics.dashboard_layout(tenant_id, shared);
