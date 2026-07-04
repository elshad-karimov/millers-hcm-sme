-- ----------------------------------------------------------------------------
-- M275 — Recruitment PRD §7: Requisition approval workflow.
--
-- Two definitions, picked by RequisitionType.isReplacementLike():
--
--   REQUISITION_APPROVAL_NEW          (full chain — new headcount adds cost)
--     1. Department manager review    ROLE_DEPARTMENT_MANAGER
--     2. HR review                    ROLE_HR_SPECIALIST
--     3. Finance / budget validation  ROLE_HR_ADMIN
--     4. Executive sign-off           ROLE_SYSTEM_ADMIN
--
--   REQUISITION_APPROVAL_REPLACEMENT  (short chain — headcount already exists)
--     1. HR review                    ROLE_HR_SPECIALIST
--     2. HR director                  ROLE_HR_ADMIN
--
-- Vacancy also gains workflow_instance_id so the SPA can deep-link the
-- requisition to its running approval instance.
-- ----------------------------------------------------------------------------

ALTER TABLE recruitment.vacancy
  ADD COLUMN IF NOT EXISTS workflow_instance_id UUID;

INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    'aaaa1111-2222-3333-4444-555566667777',
    'REQUISITION_APPROVAL_NEW',
    'Requisition approval — new headcount',
    'Full approval chain for requisitions that add headcount cost: department, HR, finance, executive.',
    false, true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
SELECT 'aaaa1111-2222-3333-4444-555566667777', v.step_order, v.name, v.role
FROM (VALUES
    (1, 'Department manager review',   'ROLE_DEPARTMENT_MANAGER'),
    (2, 'HR review',                   'ROLE_HR_SPECIALIST'),
    (3, 'Finance / budget validation', 'ROLE_HR_ADMIN'),
    (4, 'Executive sign-off',          'ROLE_SYSTEM_ADMIN')
) AS v(step_order, name, role)
WHERE NOT EXISTS (
    SELECT 1 FROM workflow.workflow_step
    WHERE definition_id = 'aaaa1111-2222-3333-4444-555566667777');

INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    'bbbb1111-2222-3333-4444-555566667777',
    'REQUISITION_APPROVAL_REPLACEMENT',
    'Requisition approval — replacement',
    'Short approval chain for replacement / internal requisitions: the headcount is already approved.',
    false, true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
SELECT 'bbbb1111-2222-3333-4444-555566667777', v.step_order, v.name, v.role
FROM (VALUES
    (1, 'HR review',   'ROLE_HR_SPECIALIST'),
    (2, 'HR director', 'ROLE_HR_ADMIN')
) AS v(step_order, name, role)
WHERE NOT EXISTS (
    SELECT 1 FROM workflow.workflow_step
    WHERE definition_id = 'bbbb1111-2222-3333-4444-555566667777');
