-- HCM_11 Benefits Administration — M380 (Phase C.2)
-- Qualifying life events. Marriage / birth / etc. open a per-employee special enrollment
-- window (event_date + window_days) so an employee may change benefits outside open enrollment.
-- HR approves the reported event; while APPROVED and within the window the employee is eligible.

CREATE TABLE compbenefits.benefit_life_event (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    varchar(64) NOT NULL DEFAULT 'default',
    employee_id  uuid NOT NULL,
    event_type   varchar(30) NOT NULL,
    event_date   date NOT NULL,
    window_days  int NOT NULL DEFAULT 30,
    status       varchar(20) NOT NULL DEFAULT 'PENDING',
    notes        text,
    reported_by  varchar(80),
    reviewed_by  varchar(80),
    reviewed_at  timestamptz,
    review_notes text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT life_event_type_check
        CHECK (event_type IN ('MARRIAGE','DIVORCE','BIRTH','ADOPTION','DEATH','DEPENDENT_LOSS','OTHER')),
    CONSTRAINT life_event_status_check
        CHECK (status IN ('PENDING','APPROVED','REJECTED','CLOSED')),
    CONSTRAINT life_event_window_pos CHECK (window_days > 0)
);

CREATE INDEX life_event_emp_idx ON compbenefits.benefit_life_event (tenant_id, employee_id, status);

COMMENT ON TABLE compbenefits.benefit_life_event IS
    'HCM_11 M380 — qualifying life events that open a special enrollment window.';
