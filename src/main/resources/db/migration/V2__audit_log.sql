-- Audit module: immutable audit log on sensitive entities (PRD Section 14.5, 16.2).
-- Monthly range-partitioning is a dedicated performance task (PRD 15.3); the slice
-- ships a single table with the same column shape.

CREATE TABLE audit.audit_log (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    actor         VARCHAR(120) NOT NULL,
    module        VARCHAR(64)  NOT NULL,
    entity_name   VARCHAR(64)  NOT NULL,
    entity_id     VARCHAR(64),
    action        VARCHAR(32)  NOT NULL,
    old_value     JSONB,
    new_value     JSONB,
    ip_address    VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_entity   ON audit.audit_log (entity_name, entity_id);
CREATE INDEX idx_audit_created  ON audit.audit_log (created_at);
CREATE INDEX idx_audit_actor    ON audit.audit_log (actor);
