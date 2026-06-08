-- V119 — Turnstile import batch tracking (M179 / PRD §17.1)
--
-- Persists metadata and failed/duplicate rows for every CSV import so that
-- duplicates can be investigated and failed rows can be retried without
-- re-uploading the whole file.

CREATE TABLE IF NOT EXISTS attendance.turnstile_import_batch (
    id               UUID         PRIMARY KEY,
    file_name        VARCHAR(255),
    imported_by      VARCHAR(120) NOT NULL,
    imported_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    total_rows       INTEGER      NOT NULL DEFAULT 0,
    imported_count   INTEGER      NOT NULL DEFAULT 0,
    duplicate_count  INTEGER      NOT NULL DEFAULT 0,
    failed_count     INTEGER      NOT NULL DEFAULT 0,
    -- COMPLETED = all rows ok; PARTIAL = some rows failed; FAILED = 0 rows imported
    status           VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED'
        CONSTRAINT turnstile_batch_status_ck CHECK (status IN ('COMPLETED','PARTIAL','FAILED'))
);

-- Only non-IMPORTED rows are stored; IMPORTED rows are represented by the
-- attendance_event rows themselves (linked via batch_id on the event is not
-- required — keep it simple: batch aggregates counts, row table holds rejects).
CREATE TABLE IF NOT EXISTS attendance.turnstile_import_row (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id      UUID        NOT NULL
        REFERENCES attendance.turnstile_import_batch(id) ON DELETE CASCADE,
    line_number   INTEGER     NOT NULL,
    raw_line      TEXT,
    employee_id   UUID,
    event_time    TIMESTAMPTZ,
    event_type    VARCHAR(20),
    device_id     VARCHAR(100),
    -- DUPLICATE = same (employee_id, event_time) already in DB; FAILED = parse/lookup error
    row_status    VARCHAR(20) NOT NULL
        CONSTRAINT turnstile_row_status_ck CHECK (row_status IN ('DUPLICATE','FAILED')),
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_turnstile_import_row_batch
    ON attendance.turnstile_import_row (batch_id, row_status);
