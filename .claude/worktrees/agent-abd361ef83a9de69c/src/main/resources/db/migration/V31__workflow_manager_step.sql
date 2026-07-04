-- Real MANAGER step role in workflows (PRD 9 — milestone 35).
--
-- Until this migration every "Manager review" step on the 7
-- employee-owned workflows used ROLE_HR_SPECIALIST as a stand-in
-- (documented in V10's seed comment: "Manager step is approximated
-- with ROLE_HR_SPECIALIST until manager hierarchy ABAC lands").
-- M22 landed the manager hierarchy and M24 made the workflow inbox
-- ABAC-aware, but every HR_SPECIALIST in the org still shared one
-- pooled "Manager review" inbox — anyone with the role could approve
-- anyone else's leave.
--
-- This change flips the 7 steps to a real manager resolution:
--   approver_role         → ROLE_DEPARTMENT_MANAGER
--   resolves_to_manager   → TRUE
-- WorkflowService then narrows the inbox + act gate to the subject's
-- direct manager (employee.manager_id), so only Aliya's actual
-- manager — not every department manager in the org — sees her leave
-- request.

ALTER TABLE workflow.workflow_step
    ADD COLUMN resolves_to_manager BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN workflow.workflow_step.resolves_to_manager IS
    'When TRUE, the approver for this step is the subject''s direct manager (via core_hr.employee.manager_id). approver_role is still required (must include the caller''s role) but is no longer sufficient — WorkflowService also checks that the caller is the resolved manager. PRD 9 / 14.9 — milestone 35.';

-- Flip the 7 employee-owned "Manager review" steps. Each definition id
-- is the deterministic UUID seeded in V10 / V13 / V14 / V18 / V19, so
-- we can target the rows directly.
UPDATE workflow.workflow_step
SET approver_role       = 'ROLE_DEPARTMENT_MANAGER',
    resolves_to_manager = TRUE
WHERE step_order = 1
  AND definition_id IN (
        '33333333-3333-3333-3333-333333333333',   -- LEAVE_REQUEST_APPROVAL
        '44444444-4444-4444-4444-444444444444',   -- BUSINESS_TRIP_APPROVAL
        '55555555-5555-5555-5555-555555555555',   -- PERMISSION_REQUEST_APPROVAL
        '66666666-6666-6666-6666-666666666666',   -- TIMESHEET_APPROVAL
        '88888888-8888-8888-8888-888888888888',   -- TERMINATION_APPROVAL
        '99999999-9999-9999-9999-999999999999',   -- CONTRACT_CHANGE_APPROVAL
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'    -- PERFORMANCE_REVIEW_APPROVAL
  )
  AND name = 'Manager review';

-- PAYROLL_APPROVAL (def 7777…) stays untouched — payroll runs are
-- org-wide, not employee-owned, so there's no manager to resolve to.
-- "Payroll review" stays ROLE_HR_SPECIALIST as designed.
