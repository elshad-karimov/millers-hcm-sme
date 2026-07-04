-- Default leave types for the Republic of Azerbaijan (PRD 8.5.1).
-- All values are configurable; these are the codified defaults.

INSERT INTO leave_mgmt.leave_type
    (code, name, paid, requires_attachment, requires_replacement,
     default_annual_entitlement_days, carry_forward_limit_days,
     exclude_weekends, exclude_holidays, active)
VALUES
    ('ANNUAL',        'Annual leave',           TRUE,  FALSE, TRUE,
     21.00, 5.00,  FALSE, TRUE,  TRUE),
    ('SICK',          'Sick leave',             TRUE,  TRUE,  FALSE,
     NULL,  0.00,  FALSE, FALSE, TRUE),
    ('MATERNITY',     'Maternity leave',        TRUE,  TRUE,  FALSE,
     126.00, NULL, FALSE, FALSE, TRUE),
    ('MARRIAGE',      'Marriage leave',         TRUE,  TRUE,  FALSE,
     3.00,  0.00,  FALSE, FALSE, TRUE),
    ('COMPASSIONATE', 'Compassionate leave',    TRUE,  TRUE,  FALSE,
     3.00,  0.00,  FALSE, FALSE, TRUE),
    ('EDUCATIONAL',   'Educational leave',      TRUE,  TRUE,  FALSE,
     NULL,  0.00,  FALSE, FALSE, TRUE),
    ('SOCIAL',        'Social leave',           TRUE,  FALSE, FALSE,
     NULL,  0.00,  FALSE, FALSE, TRUE),
    ('UNPAID',        'Unpaid leave',           FALSE, FALSE, FALSE,
     0.00,  0.00,  FALSE, FALSE, TRUE),
    -- Paternity / spouse leave during maternity — abolished by the
    -- Azerbaijan Labour Code amendment effective 16 January 2026
    -- (PRD 8.5.1). Kept as a configurable record so other jurisdictions
    -- can re-enable it, but disabled by default.
    ('PATERNITY',     'Paternity leave',        TRUE,  FALSE, FALSE,
     14.00, 0.00,  FALSE, FALSE, FALSE);

-- Workflow definition for leave requests.
-- PRD 8.5.4 specifies Employee → Direct Manager → HR. Until manager-
-- hierarchy ABAC is wired, we approximate the manager step with
-- ROLE_HR_SPECIALIST and the HR step with ROLE_HR_ADMIN.

INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    'LEAVE_REQUEST_APPROVAL',
    'Leave Request Approval',
    'Two-step approval for leave requests (PRD 8.5.4). Manager step is approximated with ROLE_HR_SPECIALIST until manager hierarchy ABAC lands.',
    FALSE,
    TRUE
);

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('33333333-3333-3333-3333-333333333333', 1, 'Manager review', 'ROLE_HR_SPECIALIST'),
    ('33333333-3333-3333-3333-333333333333', 2, 'HR approval',    'ROLE_HR_ADMIN');
