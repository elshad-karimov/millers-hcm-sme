-- HCM_11 Benefits Administration — M379 (Phase C.1)
-- Open-enrollment windows: the annual period during which employees may elect / change
-- benefits for a plan year. Governance layer + "is enrollment open now" surface; HR admin
-- enrolment is always allowed (this gates employee self-election).

CREATE TABLE compbenefits.open_enrollment_window (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   varchar(64) NOT NULL DEFAULT 'default',
    plan_year   int NOT NULL,
    name        varchar(160) NOT NULL,
    start_date  date NOT NULL,
    end_date    date NOT NULL,
    active      boolean NOT NULL DEFAULT true,
    notes       text,
    created_by  varchar(80),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT oe_window_dates CHECK (end_date >= start_date)
);

CREATE INDEX oe_window_year_idx ON compbenefits.open_enrollment_window (tenant_id, plan_year, active);

COMMENT ON TABLE compbenefits.open_enrollment_window IS
    'HCM_11 M379 — annual open-enrollment window per plan year.';
