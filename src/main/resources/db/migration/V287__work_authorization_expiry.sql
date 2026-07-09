-- ----------------------------------------------------------------------------
-- M471 — Visa / work permit expiry tracking
--
-- Adds work_authorized_until field to core_hr.employee for tracking
-- visa/work permit expiry dates. Compliance module provides expiry alerts.
-- Document types VISA and WORK_PERMIT are already available (VARCHAR storage).
-- ----------------------------------------------------------------------------

ALTER TABLE core_hr.employee
  ADD COLUMN IF NOT EXISTS work_authorized_until DATE;

CREATE INDEX IF NOT EXISTS idx_employee_work_auth_expiry
  ON core_hr.employee(work_authorized_until)
  WHERE work_authorized_until IS NOT NULL;
