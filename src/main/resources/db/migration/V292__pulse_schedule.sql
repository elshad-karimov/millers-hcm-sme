-- M477 — Pulse survey scheduling

CREATE TABLE engagement.pulse_schedule (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(100) NOT NULL DEFAULT 'default',
    survey_template_id  UUID NOT NULL REFERENCES engagement.survey_template(id),
    frequency           VARCHAR(20) NOT NULL, -- WEEKLY | BIWEEKLY | MONTHLY
    day_of_week         INT CHECK (day_of_week BETWEEN 1 AND 7), -- 1=Mon..7=Sun, for WEEKLY/BIWEEKLY
    day_of_month        INT CHECK (day_of_month BETWEEN 1 AND 28), -- for MONTHLY
    active              BOOLEAN NOT NULL DEFAULT true,
    last_run_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pulse_schedule_tenant_active ON engagement.pulse_schedule(tenant_id, active);
