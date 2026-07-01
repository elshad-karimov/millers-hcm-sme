-- M361 — Salary change request + approval workflow
-- M362 — Compensation exceptions

-- ── Workflow definition seed ──────────────────────────────────────────────────
-- SALARY_CHANGE_APPROVAL: step 1 resolves to the employee's direct manager,
-- step 2 is HR_ADMIN or COMPENSATION_MANAGER role step.

INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    'SALARY_CHANGE_APPROVAL',
    'Salary Change Approval',
    'Sequential approval for compensation changes (§30 PRD HCM_10).',
    FALSE,
    TRUE
);

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role, resolves_to_manager)
VALUES
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 1, 'Manager review', 'ROLE_DEPARTMENT_MANAGER', TRUE),
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 2, 'Compensation approval', 'ROLE_COMPENSATION_MANAGER', FALSE);

-- ── Salary change request ──────────────────────────────────────────────────────

CREATE TABLE compensation.salary_change_request (
    id                   UUID PRIMARY KEY,
    tenant_id            VARCHAR(64) NOT NULL DEFAULT 'default',
    employee_id          UUID NOT NULL REFERENCES core_hr.employee(id),
    current_salary       NUMERIC(14,2),
    proposed_salary      NUMERIC(14,2) NOT NULL,
    currency             VARCHAR(3) NOT NULL DEFAULT 'AZN',
    change_reason_id     UUID REFERENCES compensation.change_reason(id),
    effective_date       DATE NOT NULL,
    increase_pct         NUMERIC(6,2),
    outside_band         BOOLEAN NOT NULL DEFAULT FALSE,
    above_threshold      BOOLEAN NOT NULL DEFAULT FALSE,
    status               VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    workflow_instance_id UUID,
    requested_by         VARCHAR(200),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at           TIMESTAMP WITH TIME ZONE,
    applied_at           TIMESTAMP WITH TIME ZONE,
    note                 VARCHAR(1000),
    CONSTRAINT salary_change_status_chk CHECK (
        status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'APPLIED', 'CANCELLED')
    )
);

CREATE INDEX idx_salary_change_tenant_emp_status ON compensation.salary_change_request(tenant_id, employee_id, status);

-- ── Compensation exceptions ────────────────────────────────────────────────────

CREATE TABLE compensation.compensation_exception (
    id             UUID PRIMARY KEY,
    tenant_id      VARCHAR(64) NOT NULL DEFAULT 'default',
    employee_id    UUID NOT NULL REFERENCES core_hr.employee(id),
    source_type    VARCHAR(40) NOT NULL,
    source_id      UUID NOT NULL,
    exception_type VARCHAR(30) NOT NULL,
    severity       VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status         VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reason         VARCHAR(1000),
    raised_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_by    VARCHAR(200),
    resolved_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT comp_exception_type_chk CHECK (
        exception_type IN ('BELOW_BAND', 'ABOVE_BAND', 'ABOVE_THRESHOLD', 'OVER_BUDGET')
    ),
    CONSTRAINT comp_exception_severity_chk CHECK (
        severity IN ('LOW', 'MEDIUM', 'HIGH')
    ),
    CONSTRAINT comp_exception_status_chk CHECK (
        status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'WAIVED')
    )
);

CREATE INDEX idx_comp_exception_tenant_status ON compensation.compensation_exception(tenant_id, status);
CREATE INDEX idx_comp_exception_source ON compensation.compensation_exception(source_type, source_id);
