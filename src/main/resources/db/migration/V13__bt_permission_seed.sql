-- Default permission types (PRD 8.7.1, 8.7.2). All values are configurable;
-- these are the codified defaults. The PRD 8.7.4 acceptance example uses
-- PERSONAL with an annual cap of 24 hours, so that's seeded verbatim.

INSERT INTO permission.permission_type
    (code, name, annual_limit_hours, paid, requires_attachment, active)
VALUES
    ('PERSONAL', 'Personal permission',  24.00, TRUE,  FALSE, TRUE),
    ('BUSINESS', 'Business permission',  NULL,  TRUE,  FALSE, TRUE),
    ('HOURLY',   'Hourly permission',    24.00, TRUE,  FALSE, TRUE),
    ('FULL_DAY', 'Full-day permission',  16.00, TRUE,  TRUE,  TRUE);

-- Workflows seeded for BT and Permission.
-- PRD 8.6.2 specifies Employee → Manager → Finance → HR → Approved.
-- With current role inventory: HR_SPECIALIST (manager) → HR_ADMIN (Finance/HR)
-- → SYSTEM_ADMIN (CEO). Finance role becomes its own step once that role lands.
INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    '44444444-4444-4444-4444-444444444444',
    'BUSINESS_TRIP_APPROVAL',
    'Business Trip Approval',
    'Three-step approval for business trips with advance (PRD 8.6.2). Finance role is approximated until a dedicated ROLE_FINANCE_USER lands.',
    FALSE,
    TRUE
);
INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('44444444-4444-4444-4444-444444444444', 1, 'Manager review',  'ROLE_HR_SPECIALIST'),
    ('44444444-4444-4444-4444-444444444444', 2, 'Finance / HR',    'ROLE_HR_ADMIN'),
    ('44444444-4444-4444-4444-444444444444', 3, 'Executive sign-off','ROLE_SYSTEM_ADMIN');

-- PRD 8.7.3 specifies Employee → Direct Manager → Approved.
INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    '55555555-5555-5555-5555-555555555555',
    'PERMISSION_APPROVAL',
    'Permission Approval',
    'Single-step manager approval for permission / hourly absence (PRD 8.7.3).',
    FALSE,
    TRUE
);
INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('55555555-5555-5555-5555-555555555555', 1, 'Manager review', 'ROLE_HR_SPECIALIST');
