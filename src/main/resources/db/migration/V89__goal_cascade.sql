-- M130 — goal cascade / OKR alignment.
--
-- V19 added `parent_goal_id` as a self-FK but without an ON DELETE
-- clause, so deleting a parent goal would blow up with a constraint
-- violation. Tighten the FK to ON DELETE SET NULL so children survive
-- as roots when their parent is removed (mirrors the leniency we use
-- elsewhere — competency.parent_id in V87, org_unit.parent_id, etc.).
--
-- Constraint name `goal_parent_goal_id_fkey` is the PostgreSQL default
-- for an inline REFERENCES clause; the IF EXISTS guard makes this
-- migration safe to re-run on a fresh DB where the rename has already
-- been picked up.

ALTER TABLE performance.goal
    DROP CONSTRAINT IF EXISTS goal_parent_goal_id_fkey;

ALTER TABLE performance.goal
    ADD CONSTRAINT goal_parent_goal_id_fkey
    FOREIGN KEY (parent_goal_id)
    REFERENCES performance.goal (id)
    ON DELETE SET NULL;

-- ── cascade_origin: track WHO cascaded this goal from a parent ───────
--
-- Lets the SPA show "Cascaded by jane.smith from CEO-OKR-7 on 2026-06-06"
-- on a child goal, and lets the audit pass review the cascade graph
-- without hitting the audit log.

ALTER TABLE performance.goal
    ADD COLUMN IF NOT EXISTS cascaded_by   VARCHAR(160),
    ADD COLUMN IF NOT EXISTS cascaded_at   TIMESTAMPTZ;
