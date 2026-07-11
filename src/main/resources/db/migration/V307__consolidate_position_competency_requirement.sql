-- V307 — consolidate "position competency requirements + skill-gap analysis" onto
-- the canonical learning table.
--
-- Two parallel implementations existed:
--   * learning.position_competency_requirement (M60, V48) — KEEP (canonical).
--   * staffing.position_required_competency      (M127, V87) — RETIRE.
--
-- The learning table is the poorer schema: it lacks the `mandatory` flag that the
-- repointed consumers (skill-inventory critical-skills report, performance
-- competency-assessment seeding) rely on. Add it here. `required_proficiency`
-- stays as the 1..5 level column (equivalent to the retired table's required_level).
--
-- Both backing tables are empty (verified), so there is NO data to migrate.

-- (a) Add the `mandatory` flag the repointed consumers need. Default TRUE preserves
--     the prior "everything is mandatory unless said otherwise" semantics and keeps
--     any rows created via the learning requirement API mandatory by default.
ALTER TABLE learning.position_competency_requirement
    ADD COLUMN mandatory BOOLEAN NOT NULL DEFAULT TRUE;

-- (b) Drop the retired parallel table (empty — verified). Its indexes, CHECK and
--     UNIQUE constraints are dropped with it. Nothing FK-references into it, so a
--     plain DROP TABLE is sufficient.
DROP TABLE staffing.position_required_competency;
