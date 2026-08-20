-- ----------------------------------------------------------------------------
-- V324 — Two-stage timesheet approval: direct manager, then HR.
--
-- Until now `approve` took a month straight from SUBMITTED to APPROVED in one
-- action, so whoever reached it first finalised it and HR had no say. The
-- agreed route is: employee submits → their DIRECT MANAGER approves → HR
-- approves → APPROVED (and only then does payroll see a settled month).
--
-- Status gains PENDING_HR for the middle state. There is no CHECK constraint on
-- timesheet.status, so the value needs no DDL — but the two manager-stage
-- columns do: without them the first approval would overwrite approved_by/at,
-- and the record of WHO signed off at WHICH stage — the thing an audit of an
-- approval chain actually asks for — would be lost.
--
-- approved_by / approved_at keep their meaning: the FINAL (HR) approval.
-- ----------------------------------------------------------------------------

ALTER TABLE timesheet.timesheet
    ADD COLUMN IF NOT EXISTS manager_approved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS manager_approved_by VARCHAR(80);

COMMENT ON COLUMN timesheet.timesheet.manager_approved_at IS
  'Stage 1 — when the employee''s direct manager approved. Final HR sign-off is approved_at.';
COMMENT ON COLUMN timesheet.timesheet.manager_approved_by IS
  'Stage 1 — which manager approved. Final HR approver is approved_by.';

-- Months already APPROVED under the single-stage rule keep that outcome; the
-- approver recorded there acted for both stages, so mirror it into the manager
-- columns rather than leaving a gap that reads as "no manager ever approved".
UPDATE timesheet.timesheet
   SET manager_approved_at = approved_at,
       manager_approved_by = approved_by
 WHERE status = 'APPROVED'
   AND approved_at IS NOT NULL
   AND manager_approved_at IS NULL;
