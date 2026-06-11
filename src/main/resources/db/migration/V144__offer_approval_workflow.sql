-- ----------------------------------------------------------------------------
-- M276 — Recruitment PRD §29-§30: offer approval workflow + salary
-- exception routing.
--
-- Offers can no longer be SENT from DRAFT. New flow:
--
--   DRAFT → PENDING_APPROVAL → APPROVED → SENT → ACCEPTED/...
--
-- The approval chain is routed by a salary-range check against the
-- linked position (fallback: the vacancy's advertised range):
--
--   OFFER_APPROVAL            (salary within range)
--     1. HR review            ROLE_HR_SPECIALIST
--     2. HR director          ROLE_HR_ADMIN
--
--   OFFER_APPROVAL_EXCEPTION  (salary outside range — PRD §29:
--                              "require exception approval")
--     1. HR review            ROLE_HR_SPECIALIST
--     2. Compensation/Finance ROLE_HR_ADMIN
--     3. Executive sign-off   ROLE_SYSTEM_ADMIN
-- ----------------------------------------------------------------------------

ALTER TABLE recruitment.offer
  ADD COLUMN IF NOT EXISTS workflow_instance_id UUID,
  ADD COLUMN IF NOT EXISTS salary_exception     BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    'cccc1111-2222-3333-4444-555566667777',
    'OFFER_APPROVAL',
    'Offer approval — standard',
    'Offer salary within the position salary range: HR review then HR director.',
    false, true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
SELECT 'cccc1111-2222-3333-4444-555566667777', v.step_order, v.name, v.role
FROM (VALUES
    (1, 'HR review',   'ROLE_HR_SPECIALIST'),
    (2, 'HR director', 'ROLE_HR_ADMIN')
) AS v(step_order, name, role)
WHERE NOT EXISTS (
    SELECT 1 FROM workflow.workflow_step
    WHERE definition_id = 'cccc1111-2222-3333-4444-555566667777');

INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    'dddd1111-2222-3333-4444-555566667777',
    'OFFER_APPROVAL_EXCEPTION',
    'Offer approval — salary exception',
    'Offer salary outside the position salary range: adds compensation/finance validation and executive sign-off.',
    false, true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
SELECT 'dddd1111-2222-3333-4444-555566667777', v.step_order, v.name, v.role
FROM (VALUES
    (1, 'HR review',                       'ROLE_HR_SPECIALIST'),
    (2, 'Compensation / finance validation', 'ROLE_HR_ADMIN'),
    (3, 'Executive sign-off',              'ROLE_SYSTEM_ADMIN')
) AS v(step_order, name, role)
WHERE NOT EXISTS (
    SELECT 1 FROM workflow.workflow_step
    WHERE definition_id = 'dddd1111-2222-3333-4444-555566667777');
