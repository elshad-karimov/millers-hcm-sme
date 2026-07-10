-- ----------------------------------------------------------------------------
-- M491 — HCM_44 Movement execution wiring.
--
-- Adds executed_at/executed_by columns to track when HR executes an APPROVED
-- movement request. Status remains VARCHAR so adding EXECUTED value requires
-- no migration rebuild — just application-side enum update.
-- ----------------------------------------------------------------------------

ALTER TABLE lifecycle.employee_movement_request
    ADD COLUMN IF NOT EXISTS executed_at timestamptz,
    ADD COLUMN IF NOT EXISTS executed_by varchar(80);

COMMENT ON COLUMN lifecycle.employee_movement_request.executed_at IS
    'M491 — When HR executed the approved movement (TRANSFER/PROMOTION).';
COMMENT ON COLUMN lifecycle.employee_movement_request.executed_by IS
    'M491 — Username who executed the movement.';
