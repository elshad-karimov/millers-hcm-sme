-- ----------------------------------------------------------------------------
-- M429 — HCM_21 ESS Phase D.1: HR helpdesk / service request system.
--
-- Employees submit service requests (salary cert, employment letter, policy
-- question, grievance, etc.) that HR receives in a queue, assigns, resolves.
-- SLA by priority: HIGH +1 business day, NORMAL +2, LOW +5.
-- ----------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS selfservice;

CREATE SEQUENCE IF NOT EXISTS selfservice.hr_service_request_no_seq START 1;

CREATE TABLE IF NOT EXISTS selfservice.hr_service_request (
    id                  uuid PRIMARY KEY,
    tenant_id           varchar(80)  NOT NULL DEFAULT 'default',
    request_no          varchar(20)  NOT NULL UNIQUE,
    employee_id         uuid         NOT NULL,
    category            varchar(50)  NOT NULL,
    priority            varchar(20)  NOT NULL DEFAULT 'NORMAL',
    subject             varchar(300) NOT NULL,
    description         varchar(4000),
    status              varchar(20)  NOT NULL DEFAULT 'OPEN',
    assigned_to_username varchar(80),
    sla_due             timestamptz,
    resolved_at         timestamptz,
    resolution_notes    varchar(4000),
    created_at          timestamptz  NOT NULL DEFAULT now(),
    created_by          varchar(80),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    updated_by          varchar(80),
    CONSTRAINT hr_service_request_category_check CHECK (category IN (
        'SALARY_CERT', 'EMPLOYMENT_LETTER', 'PAYROLL_INQUIRY',
        'POLICY_QUESTION', 'GRIEVANCE', 'OTHER'
    )),
    CONSTRAINT hr_service_request_priority_check CHECK (priority IN (
        'LOW', 'NORMAL', 'HIGH'
    )),
    CONSTRAINT hr_service_request_status_check CHECK (status IN (
        'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'
    ))
);

CREATE INDEX IF NOT EXISTS hr_service_request_tenant_idx
    ON selfservice.hr_service_request (tenant_id);
CREATE INDEX IF NOT EXISTS hr_service_request_employee_idx
    ON selfservice.hr_service_request (employee_id);
CREATE INDEX IF NOT EXISTS hr_service_request_status_idx
    ON selfservice.hr_service_request (status, priority, sla_due);
CREATE INDEX IF NOT EXISTS hr_service_request_assigned_idx
    ON selfservice.hr_service_request (assigned_to_username)
    WHERE assigned_to_username IS NOT NULL;

COMMENT ON TABLE selfservice.hr_service_request IS
    'M429 — HR helpdesk: employee service requests (salary cert, policy question, grievance). SLA by priority.';
