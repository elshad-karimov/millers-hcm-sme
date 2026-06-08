-- M146: Position hierarchy (§8) + functional manager on Employee (§9).

-- §8 — Positions can form a parent/child hierarchy (e.g. "Senior Engineer" under
--       "Engineering Lead"). Self-FK; nullable (root positions have no parent).
ALTER TABLE staffing.position
    ADD COLUMN IF NOT EXISTS parent_position_id UUID REFERENCES staffing.position(id);

CREATE INDEX IF NOT EXISTS idx_position_parent ON staffing.position (parent_position_id);

-- §9 — Functional manager: the manager in a functional / project line of authority.
--       Distinct from manager_id (line manager driving approvals),
--       matrix_manager_id (dotted-line, informational) and
--       delegate_manager_id (temporary stand-in).
--       Soft FK to core_hr.employee — no hard FK to avoid circular dependency.
ALTER TABLE core_hr.employee
    ADD COLUMN IF NOT EXISTS functional_manager_id UUID;
