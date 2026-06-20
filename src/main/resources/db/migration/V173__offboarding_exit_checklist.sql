-- M315: offboarding exit checklist auto-creation
-- Add checklist_assignment_id link on offboarding_case
ALTER TABLE lifecycle.offboarding_case
    ADD COLUMN IF NOT EXISTS checklist_assignment_id UUID;

-- Default exit checklist template
INSERT INTO lifecycle.checklist_template (id, code, name, flow_type, active, priority)
VALUES (
    gen_random_uuid(),
    'DEFAULT_EXIT',
    'Standard Exit Checklist',
    'OFFBOARDING',
    TRUE,
    0
);

-- Tasks in step order
WITH tpl AS (SELECT id FROM lifecycle.checklist_template WHERE code = 'DEFAULT_EXIT')
INSERT INTO lifecycle.checklist_template_task
    (id, template_id, step_order, title, description, required, task_type, category)
SELECT
    gen_random_uuid(), tpl.id, t.step_order, t.title, t.description, t.required, t.task_type, t.category
FROM tpl,
(VALUES
    (1,  'Revoke system access & accounts',       'Disable all IT accounts and system access', TRUE,  'IT_ACCOUNT_REQUEST',  'IT'),
    (2,  'Collect company equipment',              'Retrieve laptop, phone, keys and access badges', TRUE,  'EQUIPMENT_REQUEST',   'EQUIPMENT_AND_ASSETS'),
    (3,  'Knowledge transfer handover',            'Employee to document processes and hand over to successor', TRUE,  'MANAGER_TASK',        'DOCUMENTATION'),
    (4,  'Conduct exit interview',                 'HR to conduct structured exit interview', TRUE,  'HR_TASK',             'HR_ADMIN'),
    (5,  'Process final settlement',               'Payroll to calculate and process final salary, accruals and statutory deductions', TRUE,  'PAYROLL_TASK',        'PAYROLL_AND_FINANCE'),
    (6,  'Cancel workspace / building access',     'Remove physical access and de-assign desk/workspace', FALSE, 'WORKSPACE_REQUEST',   'FACILITIES'),
    (7,  'Update employee records',               'Update employment status, end date and reason in HR system', TRUE,  'HR_TASK',             'HR_ADMIN'),
    (8,  'Remove from active directories & groups','Remove from email groups, Slack/Teams channels and org directories', FALSE, 'IT_ACCOUNT_REQUEST',  'IT'),
    (9,  'Archive employee data per retention policy', 'Ensure personal data is retained or anonymised per policy §46/§47', FALSE, 'SECURITY_ACCESS_TASK','COMPLIANCE')
) AS t(step_order, title, description, required, task_type, category);
