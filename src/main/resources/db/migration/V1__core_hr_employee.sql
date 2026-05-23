-- Core HR module: employee master data (PRD Section 8.1, 16.2).

-- Generates the human-readable employee number (EMP-00001, EMP-00002, ...).
CREATE SEQUENCE core_hr.employee_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE core_hr.employee (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_no         VARCHAR(32)  NOT NULL UNIQUE,

    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    middle_name         VARCHAR(100),
    birth_date          DATE,
    gender              VARCHAR(16),

    -- PII. Column-level AES-256 encryption is a dedicated hardening task (PRD 14.3/14.4).
    national_id         VARCHAR(64),

    email               VARCHAR(160) UNIQUE,
    phone               VARCHAR(32),

    hire_date           DATE         NOT NULL,
    employment_status   VARCHAR(32)  NOT NULL,

    -- Free-text placeholders until the Organization / Staffing modules exist.
    department_name     VARCHAR(160),
    position_title      VARCHAR(160),
    cost_centre         VARCHAR(64),

    manager_id          UUID         REFERENCES core_hr.employee (id),

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(120),
    updated_by          VARCHAR(120)
);

CREATE INDEX idx_employee_status      ON core_hr.employee (employment_status);
CREATE INDEX idx_employee_manager     ON core_hr.employee (manager_id);
CREATE INDEX idx_employee_last_name   ON core_hr.employee (last_name);
