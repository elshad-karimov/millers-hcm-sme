-- Route the second timesheet sign-off to the employee's NAMED timesheet
-- approver instead of the ROLE_HR_ADMIN pool.
--
-- V325 gave every employee a `timesheet_approver_id` ("null = fall back to the
-- line manager") and the employee form has captured it ever since. Nothing read
-- it: TIMESHEET_APPROVAL step 2 was 'Payroll sign-off' / ROLE_HR_ADMIN, so any
-- HR admin in the tenant could approve any employee's month and the person HR
-- actually nominated had no standing at all.
--
-- This edition's approval chain is short and fixed — the employee's direct
-- manager, then their timesheet approver — so the flag mirrors
-- resolves_to_manager (V31) and resolves_to_hrbp (V100): a step that names a
-- PERSON, resolved from the subject, rather than pooling to a role.

ALTER TABLE workflow.workflow_step
    ADD COLUMN IF NOT EXISTS resolves_to_timesheet_approver BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN workflow.workflow_step.resolves_to_timesheet_approver IS
    'When TRUE the approver for this step is the subject''s named timesheet '
    'approver (core_hr.employee.timesheet_approver_id), single-hop delegation '
    'applied. Unlike resolves_to_manager, approver_role is NOT also required: '
    'the nominated approver is a named individual who may hold no manager or HR '
    'role, and identity is the stricter gate of the two. WorkflowService skips '
    'the step entirely when the subject has no approver named, or when the '
    'resolved approver is the person who just approved the previous step — a '
    'second signature from the same hand is not a second signature.';

-- TIMESHEET_APPROVAL (V14) step 2. approver_role stays populated because SLA
-- escalation and the role-based inbox query both read it; ROLE_DEPARTMENT_MANAGER
-- matches step 1 so a manager-approver's inbox query still finds the row (the
-- identity filter then narrows it), while an approver holding no such role is
-- picked up by the named-approver inbox path.
UPDATE workflow.workflow_step
SET name                           = 'Timesheet approver',
    approver_role                  = 'ROLE_DEPARTMENT_MANAGER',
    resolves_to_timesheet_approver = TRUE
WHERE definition_id = '66666666-6666-6666-6666-666666666666'
  AND step_order = 2;
