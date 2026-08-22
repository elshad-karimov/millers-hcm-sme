-- Timesheet approval: add the HR stage.
--
-- The chain was meant to be "direct manager, then HR". What was actually
-- seeded is:
--
--   step 1  Manager review      resolves_to_manager
--   step 2  Timesheet approver  resolves_to_timesheet_approver
--
-- and nothing routes to HR at all. Worse, step 2 is skipped when the employee
-- has no named timesheet approver — the engine treats an unresolvable named
-- approver as a step to pass over — so for anyone without that field set, ONE
-- manager approval took the month straight to APPROVED and into payroll.
-- Verified on the live system: a submitted month went SUBMITTED -> APPROVED in
-- a single call, with manager_approved_at never set.
--
-- Step 3 is role-based rather than a named person: the workbook names an HR
-- verifier per employee, but the engine has no resolver for that field, and a
-- role the whole HR team holds is better than a chain that silently skips when
-- one person's record is blank — which is the bug being fixed here.
INSERT INTO workflow.workflow_step
    (id, tenant_id, definition_id, step_order, name, approver_role,
     resolves_to_manager, resolves_to_hrbp, resolves_to_timesheet_approver,
     parallel, escalation_action)
SELECT gen_random_uuid(), 'default',
       '66666666-6666-6666-6666-666666666666', 3, 'HR verification', 'ROLE_HR_ADMIN',
       FALSE, FALSE, FALSE, FALSE, 'NOTIFY'
 WHERE NOT EXISTS (
     SELECT 1 FROM workflow.workflow_step
      WHERE definition_id = '66666666-6666-6666-6666-666666666666'
        AND step_order = 3
 );
