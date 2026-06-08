-- M202: Seed missing workflow definitions for headcount change requests and
-- bonus run approvals.  Both modules have been using these codes since M156
-- and M200 respectively but the workflow_definition rows were never inserted,
-- meaning WorkflowService.start() would throw "definition not found" at runtime.

-- Headcount Change Request approval (PRD §8.3.7).
-- Dept Head submits → HR reviews → Executive approves.
INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    'HEADCOUNT_CHANGE_REQUEST',
    'Headcount Change Request',
    'Two-step approval for headcount increases / decreases on a position (PRD §8.3.7).',
    FALSE,
    TRUE
);

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 1, 'HR review',         'ROLE_HR_ADMIN'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 2, 'Executive sign-off', 'ROLE_SYSTEM_ADMIN');

-- Bonus Run Approval (PRD §8.13.12).
-- HR generates a bonus run → Finance Director approves → HR pushes to payroll.
INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'BONUS_RUN_APPROVAL',
    'Bonus Run Approval',
    'Two-step approval for a bonus run before it can be pushed to payroll (PRD §8.13.12).',
    FALSE,
    TRUE
);

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 1, 'Finance / HR Director review', 'ROLE_HR_ADMIN'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 2, 'Executive sign-off',           'ROLE_SYSTEM_ADMIN');
