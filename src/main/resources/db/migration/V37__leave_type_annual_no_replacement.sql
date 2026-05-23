-- V37: ANNUAL leave should not require a replacement employee.
-- Employees submit their own leave requests; HR/manager may note a replacement
-- during the approval step if needed.  The original seed had requires_replacement=TRUE
-- from an HR-managed workflow assumption that no longer applies with self-service.
UPDATE leave_mgmt.leave_type
SET requires_replacement = FALSE
WHERE code = 'ANNUAL';
