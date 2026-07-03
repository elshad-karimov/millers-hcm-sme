-- HCM_12 Performance Management — M390 (Phase A.3)
-- KPI library + per-employee/cycle assignment + period measurements (PRD §5.5 / §7).
-- Scoring (§7.4): achievement % = actual / target × 100;
--   LINEAR    → rating = achievement mapped proportionally onto 1..5 (clamped),
--   THRESHOLD → banded: >=110→5, >=100→4, >=80→3, >=60→2, else 1.
-- External data sources (ERP/CRM/API §7.3) are a documented seam — entry is manual/manager.

CREATE TABLE performance.kpi (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        varchar(64) NOT NULL DEFAULT 'default',
    kpi_code         varchar(40) NOT NULL,
    kpi_name         varchar(200) NOT NULL,
    category         varchar(60),
    description      text,
    measurement_unit varchar(40),
    default_target   numeric(14,2),
    min_threshold    numeric(14,2),
    max_threshold    numeric(14,2),
    frequency        varchar(20) NOT NULL DEFAULT 'ANNUAL',
    scoring_model    varchar(20) NOT NULL DEFAULT 'LINEAR',
    data_source      varchar(30) NOT NULL DEFAULT 'MANUAL',
    active           boolean NOT NULL DEFAULT true,
    created_by       varchar(80),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT kpi_code_unique UNIQUE (tenant_id, kpi_code),
    CONSTRAINT kpi_frequency_check CHECK (frequency IN ('MONTHLY','QUARTERLY','SEMI_ANNUAL','ANNUAL')),
    CONSTRAINT kpi_scoring_check CHECK (scoring_model IN ('LINEAR','THRESHOLD')),
    CONSTRAINT kpi_source_check CHECK (data_source IN ('MANUAL','MANAGER','EMPLOYEE','IMPORT','EXTERNAL'))
);

CREATE TABLE performance.kpi_assignment (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           varchar(64) NOT NULL DEFAULT 'default',
    kpi_id              uuid NOT NULL REFERENCES performance.kpi (id),
    cycle_id            uuid NOT NULL REFERENCES performance.review_cycle (id),
    employee_id         uuid NOT NULL,
    assigned_target     numeric(14,2) NOT NULL,
    weight_percent      numeric(5,2) NOT NULL DEFAULT 0,
    actual_value        numeric(14,2),
    achievement_percent numeric(7,2),
    rating              numeric(4,2),
    status              varchar(20) NOT NULL DEFAULT 'ASSIGNED',
    assigned_by         varchar(80),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT kpi_assignment_unique UNIQUE (kpi_id, cycle_id, employee_id),
    CONSTRAINT kpi_assignment_status_check CHECK (status IN ('ASSIGNED','IN_PROGRESS','MEASURED','CANCELLED'))
);
CREATE INDEX kpi_assignment_emp_idx ON performance.kpi_assignment (tenant_id, cycle_id, employee_id);

CREATE TABLE performance.kpi_result (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           varchar(64) NOT NULL DEFAULT 'default',
    assignment_id       uuid NOT NULL REFERENCES performance.kpi_assignment (id) ON DELETE CASCADE,
    period_label        varchar(40),
    actual_value        numeric(14,2) NOT NULL,
    achievement_percent numeric(7,2),
    rating              numeric(4,2),
    note                varchar(500),
    recorded_by         varchar(80),
    recorded_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX kpi_result_assignment_idx ON performance.kpi_result (assignment_id);

COMMENT ON TABLE performance.kpi IS 'HCM_12 M390 — reusable KPI library (PRD §5.5).';
COMMENT ON TABLE performance.kpi_assignment IS 'HCM_12 M390 — KPI assigned to an employee for a cycle; holds the latest measurement.';
COMMENT ON TABLE performance.kpi_result IS 'HCM_12 M390 — per-period measurement history for an assignment.';
