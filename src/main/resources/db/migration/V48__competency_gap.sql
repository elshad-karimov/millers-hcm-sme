-- M60 (PRD Could-Have §7.3): Competency gap analysis with training recommendations.
-- Adds a requirements table that maps positions to required competency levels.
-- The gap analysis service compares these requirements against employee_competency
-- records and surfaces course recommendations for unmet requirements.

CREATE TABLE IF NOT EXISTS learning.position_competency_requirement (
    -- Soft reference to staffing.position — intentionally no FK so positions can
    -- be deleted without cascading into learning data (same pattern as
    -- core_hr.employee.org_unit_id).
    position_id          UUID    NOT NULL,
    competency_id        UUID    NOT NULL REFERENCES learning.competency (id) ON DELETE CASCADE,
    required_proficiency INTEGER NOT NULL,
    notes                TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (position_id, competency_id),
    CONSTRAINT chk_pcr_proficiency CHECK (required_proficiency BETWEEN 1 AND 5)
);

CREATE INDEX IF NOT EXISTS idx_pcr_position
    ON learning.position_competency_requirement (position_id);
