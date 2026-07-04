-- Permission / hourly absence module (PRD Section 8.7).
-- Per-employee, per-type, per-year balance in HOURS (vs. days for leave).

CREATE SEQUENCE permission.permission_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE permission.permission_type (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code                     VARCHAR(64)  NOT NULL UNIQUE,
    name                     VARCHAR(200) NOT NULL,
    description              TEXT,

    -- NULL means "no annual cap"; otherwise hours/year (PRD 8.7.2).
    annual_limit_hours       NUMERIC(7,2),

    paid                     BOOLEAN      NOT NULL DEFAULT TRUE,
    requires_attachment      BOOLEAN      NOT NULL DEFAULT FALSE,

    active                   BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               VARCHAR(120),
    updated_by               VARCHAR(120)
);

CREATE TABLE permission.permission_balance (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id              UUID         NOT NULL,
    permission_type_id       UUID         NOT NULL REFERENCES permission.permission_type (id),
    year                     INTEGER      NOT NULL,

    limit_hours              NUMERIC(7,2) NOT NULL DEFAULT 0,
    adjustment_hours         NUMERIC(7,2) NOT NULL DEFAULT 0,
    used_hours               NUMERIC(7,2) NOT NULL DEFAULT 0,
    reserved_hours           NUMERIC(7,2) NOT NULL DEFAULT 0,

    last_recalculated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (employee_id, permission_type_id, year),
    CONSTRAINT chk_pb_non_negative CHECK (used_hours >= 0 AND reserved_hours >= 0)
);

CREATE INDEX idx_pb_employee_year ON permission.permission_balance (employee_id, year);

CREATE TABLE permission.permission_request (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    request_no               VARCHAR(32)  NOT NULL UNIQUE,

    employee_id              UUID         NOT NULL,
    permission_type_id       UUID         NOT NULL REFERENCES permission.permission_type (id),

    permission_date          DATE         NOT NULL,
    start_time               TIME,
    end_time                 TIME,
    duration_hours           NUMERIC(7,2) NOT NULL,

    reason                   TEXT,
    attachment_url           VARCHAR(500),

    -- DRAFT, PENDING, APPROVED, REJECTED, CANCELLED
    status                   VARCHAR(32)  NOT NULL,
    workflow_instance_id     UUID,

    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               VARCHAR(120),

    CONSTRAINT chk_pr_hours CHECK (duration_hours > 0),
    CONSTRAINT chk_pr_times CHECK (
        (start_time IS NULL AND end_time IS NULL)
        OR (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
    )
);

CREATE INDEX idx_pr_employee ON permission.permission_request (employee_id, permission_date);
CREATE INDEX idx_pr_status   ON permission.permission_request (status);
CREATE INDEX idx_pr_type     ON permission.permission_request (permission_type_id);
