-- Default workflow definitions.
-- Once the definition admin UI lands these become configurable; for now they
-- are the canonical sequences for approvals that already run in code.

-- Org structure approval (PRD 8.2.4 — adapted to the role set available today).
INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'ORG_STRUCTURE_APPROVAL',
    'Org Structure Approval',
    'Sequential approval for organizational structure changes (PRD 8.2.4).',
    FALSE,
    TRUE
);

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('11111111-1111-1111-1111-111111111111', 1, 'HR review',        'ROLE_HR_ADMIN'),
    ('11111111-1111-1111-1111-111111111111', 2, 'Executive sign-off','ROLE_SYSTEM_ADMIN');

-- Employee status change — single-step HR approval. Wires up later; the
-- definition is here so the inbox UI can show it.
INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    'EMPLOYEE_STATUS_CHANGE',
    'Employee Status Change',
    'HR approval for changing employment status outside the standard lifecycle.',
    FALSE,
    TRUE
);

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('22222222-2222-2222-2222-222222222222', 1, 'HR approval', 'ROLE_HR_ADMIN');
