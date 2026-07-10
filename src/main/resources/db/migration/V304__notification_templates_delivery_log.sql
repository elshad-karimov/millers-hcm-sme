-- ----------------------------------------------------------------------------
-- M493 — HCM_47 Notification templates + delivery log.
--
-- Templates = reusable notification text (subject/body) with variable
-- substitution. delivery_log = audit trail of sent notifications (all
-- channels). Wire delivery_log inserts into NotificationService + EmailService
-- send paths (non-fatal).
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS notification.notification_template (
    id              uuid         PRIMARY KEY,
    tenant_id       varchar(80)  NOT NULL DEFAULT 'default',
    code            varchar(80)  NOT NULL,
    name            varchar(240) NOT NULL,
    channel         varchar(20)  NOT NULL,
    subject_template text,
    body_template   text         NOT NULL,
    active          boolean      NOT NULL DEFAULT true,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      varchar(80),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      varchar(80),

    CONSTRAINT notification_template_channel_check
        CHECK (channel IN ('EMAIL', 'IN_APP')),
    UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_notification_template_tenant
    ON notification.notification_template (tenant_id);
CREATE INDEX IF NOT EXISTS idx_notification_template_active
    ON notification.notification_template (active) WHERE active = true;

CREATE TABLE IF NOT EXISTS notification.delivery_log (
    id              uuid         PRIMARY KEY,
    tenant_id       varchar(80)  NOT NULL DEFAULT 'default',
    channel         varchar(20)  NOT NULL,
    recipient       varchar(255) NOT NULL,
    subject         varchar(300),
    status          varchar(20)  NOT NULL,
    error_message   varchar(500),
    sent_at         timestamptz  NOT NULL DEFAULT now(),
    source_module   varchar(60),

    CONSTRAINT delivery_log_channel_check
        CHECK (channel IN ('EMAIL', 'IN_APP', 'PUSH')),
    CONSTRAINT delivery_log_status_check
        CHECK (status IN ('SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_delivery_log_tenant_sent_at
    ON notification.delivery_log (tenant_id, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_delivery_log_status
    ON notification.delivery_log (status) WHERE status = 'FAILED';

COMMENT ON TABLE notification.notification_template IS
    'M493 — Notification templates. Reusable text with variable substitution.';
COMMENT ON TABLE notification.delivery_log IS
    'M493 — Notification delivery audit log. All channels.';
