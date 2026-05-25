-- M53: daily encrypted database backup log (PRD §14.4)
CREATE SCHEMA IF NOT EXISTS backup;

CREATE TABLE backup.backup_log (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ,
    status          VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',  -- RUNNING | SUCCESS | FAILED | VERIFIED
    object_key      VARCHAR(512),                              -- MinIO object key
    size_bytes      BIGINT,
    checksum_sha256 VARCHAR(64),
    error_message   TEXT,
    triggered_by    VARCHAR(255) NOT NULL DEFAULT 'scheduler',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ON backup.backup_log (started_at DESC);
