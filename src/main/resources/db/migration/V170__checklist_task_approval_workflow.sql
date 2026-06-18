-- M309 — Phase D.2: CHECKLIST_TASK_APPROVAL workflow definition.
-- Spawned automatically for every APPROVAL_TASK in an onboarding checklist;
-- routed to the hire's direct manager (resolves_to_manager = true, 48-h SLA).
-- APPROVED  → checklist task DONE   (OnboardingApprovalService closes the loop)
-- REJECTED  → checklist task PENDING (HR can re-open or reassign)

INSERT INTO workflow.workflow_definition
    (id, code, name, description, auto_approve, active, created_at, updated_at)
VALUES (
    'ccde0309-0000-0000-0000-000000000001',
    'CHECKLIST_TASK_APPROVAL',
    'Checklist Task Approval',
    'Manager approval for onboarding APPROVAL_TASK checklist items.',
    false, true, now(), now()
);

INSERT INTO workflow.workflow_step
    (id, definition_id, step_order, name, approver_role,
     resolves_to_manager, resolves_to_hrbp, sla_hours,
     escalation_action, escalation_to, parallel, condition_spel)
VALUES (
    'ccde0309-0000-0000-0001-000000000001',
    'ccde0309-0000-0000-0000-000000000001',
    0,
    'Manager Approval',
    'ROLE_DEPARTMENT_MANAGER',
    true, false, 48,
    'NOTIFY', null, false, null
);
