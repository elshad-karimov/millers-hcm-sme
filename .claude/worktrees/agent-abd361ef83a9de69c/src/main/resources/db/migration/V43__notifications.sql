-- M52: Push notification engine (PRD §17.5 + Must-Have #34)
-- Notification inbox (plain table, indexed) + device token registry.
-- Partitioning can be added in a later migration once volume justifies it;
-- for MVP a composite index on (recipient, created_at) is sufficient.

CREATE SCHEMA IF NOT EXISTS notification;

-- ── Notification log ──────────────────────────────────────────────────────────
CREATE TABLE notification.notification_log (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient     VARCHAR(255) NOT NULL,        -- Keycloak username
    channel       VARCHAR(20)  NOT NULL,        -- EMAIL | PUSH | IN_APP
    title         VARCHAR(255) NOT NULL,
    body          TEXT         NOT NULL,
    module        VARCHAR(60),
    entity_type   VARCHAR(60),
    entity_id     VARCHAR(60),
    read_at       TIMESTAMPTZ,
    sent_at       TIMESTAMPTZ,
    failed_at     TIMESTAMPTZ,
    error_message TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ON notification.notification_log (recipient, created_at DESC);
CREATE INDEX ON notification.notification_log (recipient, read_at) WHERE read_at IS NULL;

-- ── Device token registry ─────────────────────────────────────────────────────
-- Stores FCM push tokens registered from the Flutter mobile app (M51).
CREATE TABLE notification.device_token (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username   VARCHAR(255) NOT NULL,
    fcm_token  TEXT         NOT NULL,
    platform   VARCHAR(10)  NOT NULL DEFAULT 'FLUTTER',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (username, fcm_token)
);

CREATE INDEX ON notification.device_token (username);
