-- Acting / delegate manager support (PRD 9 / 14.9 — milestone 37).
--
-- Extends M35's "real MANAGER step" with time-bound delegation.
-- When a manager goes on leave, their approval inbox needs to keep
-- moving — without this, every leave / BT / permission / timesheet /
-- termination / contract-change / performance-review request routed
-- to that manager would block until they came back.
--
-- Design: time-bound delegate columns directly on employee. Cheaper
-- than a separate `delegation` table (one row per manager + lifetime,
-- not per-event), simpler resolver in WorkflowService (one extra
-- field lookup + a date-range check, no JOIN).
--
--   delegate_manager_id  — the deputy this manager has delegated to
--   delegate_from        — first date the delegation is active
--   delegate_to          — last date the delegation is active (inclusive)
--
-- A constraint check enforces all-three-null or all-three-set with
-- start <= end, so the column triplet is never half-populated.
-- A second check refuses self-delegation (would be a noop cycle).

ALTER TABLE core_hr.employee
    ADD COLUMN delegate_manager_id UUID
        REFERENCES core_hr.employee (id),
    ADD COLUMN delegate_from       DATE,
    ADD COLUMN delegate_to         DATE,

    ADD CONSTRAINT chk_employee_delegation_window CHECK (
        (delegate_manager_id IS NULL
            AND delegate_from IS NULL
            AND delegate_to   IS NULL)
        OR
        (delegate_manager_id IS NOT NULL
            AND delegate_from IS NOT NULL
            AND delegate_to   IS NOT NULL
            AND delegate_from <= delegate_to)
    ),

    ADD CONSTRAINT chk_employee_delegation_not_self CHECK (
        delegate_manager_id IS NULL OR delegate_manager_id <> id
    );

-- Partial index — most employees never set this. Indexed only when
-- non-null so cold queries don't pay for a sparse column.
CREATE INDEX idx_employee_delegate_manager
    ON core_hr.employee (delegate_manager_id)
    WHERE delegate_manager_id IS NOT NULL;

COMMENT ON COLUMN core_hr.employee.delegate_manager_id IS
    'When non-null and today() is within [delegate_from, delegate_to], WorkflowService.resolveSubjectManagerId returns this employee instead of the original manager. Single hop only — no chain walk. PRD 9 / 14.9 — milestone 37.';
