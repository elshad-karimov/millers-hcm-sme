-- M171 (PRD §15.3): Declarative monthly RANGE partitioning for the two
-- highest-volume tables that were not converted in earlier milestones.
--
--   attendance.attendance_event — millions of turnstile records per year.
--   notification.notification_log — one row per notification sent.
--
-- Migration plan (same pattern as V26 for audit_log):
--   1. Rename original table → *_legacy
--   2. Create new partitioned parent with composite PK (id, <partition_col>)
--   3. Create monthly partitions 2025-09 through 2027-01 + DEFAULT
--   4. Copy rows from legacy
--   5. Drop legacy
--   6. Recreate supporting indexes on the parent (cascade to partitions)
--
-- The application's Hibernate entities keep @Id UUID id; Hibernate validate
-- checks column presence, not PK composition, so no entity change is needed
-- (confirmed by the existing audit_log pattern, also composite PK).

-- ─────────────────────────────────────────────────────────────────────────────
-- Part 1: attendance.attendance_event
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE attendance.attendance_event RENAME TO attendance_event_legacy;
ALTER INDEX attendance.idx_event_employee_time RENAME TO idx_event_employee_time_legacy;
ALTER INDEX attendance.idx_event_time          RENAME TO idx_event_time_legacy;

CREATE TABLE attendance.attendance_event (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    employee_id          UUID         NOT NULL,
    device_employee_code VARCHAR(64),
    event_time           TIMESTAMPTZ  NOT NULL,
    event_type           VARCHAR(8)   NOT NULL,
    device_id            VARCHAR(64),
    location             VARCHAR(160),
    source               VARCHAR(32)  NOT NULL,
    imported_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, event_time),
    CONSTRAINT chk_event_type CHECK (event_type IN ('IN', 'OUT'))
) PARTITION BY RANGE (event_time);

CREATE INDEX idx_event_employee_time ON attendance.attendance_event (employee_id, event_time);
CREATE INDEX idx_event_time          ON attendance.attendance_event (event_time);

DO $$
DECLARE
    start_month DATE := DATE '2025-09-01';
    end_month   DATE := DATE '2027-01-01';
    m           DATE := start_month;
    next_m      DATE;
    part_name   TEXT;
BEGIN
    WHILE m < end_month LOOP
        next_m    := (m + INTERVAL '1 month')::date;
        part_name := 'attendance_event_' || to_char(m, 'YYYY_MM');
        EXECUTE format(
            'CREATE TABLE attendance.%I PARTITION OF attendance.attendance_event FOR VALUES FROM (%L) TO (%L)',
            part_name, m, next_m);
        m := next_m;
    END LOOP;
END $$;

CREATE TABLE attendance.attendance_event_default PARTITION OF attendance.attendance_event DEFAULT;

INSERT INTO attendance.attendance_event
    (id, employee_id, device_employee_code, event_time, event_type,
     device_id, location, source, imported_at)
SELECT id, employee_id, device_employee_code, event_time, event_type,
       device_id, location, source, imported_at
  FROM attendance.attendance_event_legacy;

DROP TABLE attendance.attendance_event_legacy;

-- ─────────────────────────────────────────────────────────────────────────────
-- Part 2: notification.notification_log
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE notification.notification_log RENAME TO notification_log_legacy;

CREATE TABLE notification.notification_log (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    recipient     VARCHAR(255) NOT NULL,
    channel       VARCHAR(20)  NOT NULL,
    title         VARCHAR(255) NOT NULL,
    body          TEXT         NOT NULL,
    module        VARCHAR(60),
    entity_type   VARCHAR(60),
    entity_id     VARCHAR(60),
    read_at       TIMESTAMPTZ,
    sent_at       TIMESTAMPTZ,
    failed_at     TIMESTAMPTZ,
    error_message TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE INDEX ON notification.notification_log (recipient, created_at DESC);
CREATE INDEX ON notification.notification_log (recipient, read_at) WHERE read_at IS NULL;

DO $$
DECLARE
    start_month DATE := DATE '2025-09-01';
    end_month   DATE := DATE '2027-01-01';
    m           DATE := start_month;
    next_m      DATE;
    part_name   TEXT;
BEGIN
    WHILE m < end_month LOOP
        next_m    := (m + INTERVAL '1 month')::date;
        part_name := 'notification_log_' || to_char(m, 'YYYY_MM');
        EXECUTE format(
            'CREATE TABLE notification.%I PARTITION OF notification.notification_log FOR VALUES FROM (%L) TO (%L)',
            part_name, m, next_m);
        m := next_m;
    END LOOP;
END $$;

CREATE TABLE notification.notification_log_default PARTITION OF notification.notification_log DEFAULT;

INSERT INTO notification.notification_log
    (id, recipient, channel, title, body, module, entity_type, entity_id,
     read_at, sent_at, failed_at, error_message, created_at)
SELECT id, recipient, channel, title, body, module, entity_type, entity_id,
       read_at, sent_at, failed_at, error_message, created_at
  FROM notification.notification_log_legacy;

DROP TABLE notification.notification_log_legacy;
