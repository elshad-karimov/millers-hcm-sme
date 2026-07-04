-- ----------------------------------------------------------------------------
-- M430 — HCM_21 ESS Phase D.2: announcements.
--
-- HR admins post announcements (company news, policy updates, reminders) that
-- appear on employees' MyWorkspace dashboard. Audience can be ALL, DEPARTMENT,
-- or LOCATION. Publish window (from/to dates).
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS selfservice.announcement (
    id              uuid PRIMARY KEY,
    tenant_id       varchar(80)  NOT NULL DEFAULT 'default',
    title           varchar(300) NOT NULL,
    body            varchar(4000),
    publish_from    date         NOT NULL,
    publish_to      date,
    audience        varchar(20)  NOT NULL DEFAULT 'ALL',
    audience_ref    uuid,
    active          boolean      NOT NULL DEFAULT true,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      varchar(80),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      varchar(80),
    CONSTRAINT announcement_audience_check CHECK (audience IN ('ALL', 'DEPARTMENT', 'LOCATION'))
);

CREATE INDEX IF NOT EXISTS announcement_tenant_idx
    ON selfservice.announcement (tenant_id);
CREATE INDEX IF NOT EXISTS announcement_active_window_idx
    ON selfservice.announcement (active, publish_from, publish_to)
    WHERE active = true;
CREATE INDEX IF NOT EXISTS announcement_audience_idx
    ON selfservice.announcement (audience, audience_ref)
    WHERE audience_ref IS NOT NULL;

COMMENT ON TABLE selfservice.announcement IS
    'M430 — HR announcements (company news, policy updates). Audience can be ALL, DEPARTMENT, or LOCATION. Publish window.';
