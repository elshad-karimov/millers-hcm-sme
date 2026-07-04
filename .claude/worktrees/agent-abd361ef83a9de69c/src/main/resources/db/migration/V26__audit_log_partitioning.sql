-- Convert audit.audit_log to PostgreSQL declarative range-partitioning by
-- month on created_at (PRD 15.3). Partitioned tables let us drop old
-- months wholesale, vacuum each month independently, and keep indexes
-- small.
--
-- Migration plan:
--   1. Rename original table → audit_log_legacy
--   2. Create new partitioned parent table
--   3. Create monthly partitions covering the span of existing data
--      plus 3 months ahead, with a DEFAULT partition catching outliers
--   4. Copy rows from legacy into the partitioned table
--   5. Drop legacy
--   6. Recreate the original supporting indexes (on the parent — they
--      cascade to each partition)
--
-- PRIMARY KEY note: declarative partitioning requires the partition key
-- column to be part of the primary key. We move from (id) to (id, created_at).

ALTER TABLE audit.audit_log RENAME TO audit_log_legacy;
ALTER INDEX audit.idx_audit_entity  RENAME TO idx_audit_entity_legacy;
ALTER INDEX audit.idx_audit_created RENAME TO idx_audit_created_legacy;
ALTER INDEX audit.idx_audit_actor   RENAME TO idx_audit_actor_legacy;

CREATE TABLE audit.audit_log (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    actor         VARCHAR(120) NOT NULL,
    module        VARCHAR(64)  NOT NULL,
    entity_name   VARCHAR(64)  NOT NULL,
    entity_id     VARCHAR(64),
    action        VARCHAR(32)  NOT NULL,
    old_value     JSONB,
    new_value     JSONB,
    ip_address    VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE INDEX idx_audit_entity   ON audit.audit_log (entity_name, entity_id);
CREATE INDEX idx_audit_created  ON audit.audit_log (created_at);
CREATE INDEX idx_audit_actor    ON audit.audit_log (actor);

-- Create monthly partitions for 2025-09 (covers older test data) through
-- 2026-12 + a catch-all DEFAULT partition. The application's
-- AuditPartitionMaintenance scheduled job creates the next month's
-- partition on the 25th going forward.
DO $$
DECLARE
    start_month DATE := DATE '2025-09-01';
    end_month   DATE := DATE '2027-01-01';
    m           DATE := start_month;
    next_m      DATE;
    part_name   TEXT;
BEGIN
    WHILE m < end_month LOOP
        next_m   := (m + INTERVAL '1 month')::date;
        part_name := 'audit_log_' || to_char(m, 'YYYY_MM');
        EXECUTE format(
            'CREATE TABLE audit.%I PARTITION OF audit.audit_log FOR VALUES FROM (%L) TO (%L)',
            part_name, m, next_m);
        m := next_m;
    END LOOP;
END $$;

CREATE TABLE audit.audit_log_default PARTITION OF audit.audit_log DEFAULT;

INSERT INTO audit.audit_log
    (id, actor, module, entity_name, entity_id, action,
     old_value, new_value, ip_address, created_at)
SELECT id, actor, module, entity_name, entity_id, action,
       old_value, new_value, ip_address, created_at
  FROM audit.audit_log_legacy;

DROP TABLE audit.audit_log_legacy;
