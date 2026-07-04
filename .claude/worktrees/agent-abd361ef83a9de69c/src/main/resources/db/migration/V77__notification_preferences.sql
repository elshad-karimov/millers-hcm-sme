-- M115 — Per-user notification preferences.
--
-- Every existing event-driven notification (expiry alerts, leave approvals,
-- report deliveries, probation reminders, learning reminders, stale-pool
-- reminders, etc.) fires unconditionally today. Users have no opt-out.
-- This table lets a user disable a specific (category, channel) pair while
-- keeping everything else flowing. Absence of a row = the default, which
-- is opt-IN for every category and every channel.

CREATE TABLE notification.notification_preference (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    username    varchar(120) NOT NULL,
    category    varchar(60)  NOT NULL,
    channel     varchar(20)  NOT NULL,
    enabled     boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT notification_preference_channel_check
        CHECK (channel IN ('EMAIL', 'PUSH', 'IN_APP'))
);

CREATE UNIQUE INDEX notification_preference_uk
    ON notification.notification_preference (username, category, channel);

CREATE INDEX notification_preference_username_idx
    ON notification.notification_preference (username);

COMMENT ON TABLE notification.notification_preference IS
    'M115 — Per-user, per-category, per-channel opt-out. Absence = opt-in (the default).';
COMMENT ON COLUMN notification.notification_preference.category IS
    'Stable string key from NotificationCategory enum on the JVM side. Validated by the service.';
