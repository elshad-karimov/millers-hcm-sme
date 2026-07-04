-- HCM_16 M415 — bridge succession nominations to development plans (PRD §16.5)
-- Links nominees to their dev plans so the replacement chart shows how each
-- successor is being prepared for the critical position.

CREATE TABLE IF NOT EXISTS performance.succession_dev_action (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    nomination_id   UUID NOT NULL REFERENCES performance.succession_nomination (id),
    dev_plan_id     UUID NOT NULL,  -- FK performance.development_plan (M399)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (nomination_id, dev_plan_id)
);

CREATE INDEX idx_succ_dev_tenant ON performance.succession_dev_action (tenant_id);
CREATE INDEX idx_succ_dev_nom ON performance.succession_dev_action (nomination_id);

COMMENT ON TABLE performance.succession_dev_action IS 'HCM_16 M415 — link nominations to development plans for replacement chart';
