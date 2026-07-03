-- HCM_12 Performance Management — M393 (Phase B.2)
-- Structured competency assessment per review (PRD §17). Rows are seeded from the
-- employee's position requirements (staffing.position_required_competency snapshot
-- at init time) and can be added manually. Levels 1..5; §17.3 gap = required − final
-- (positive = development need). Feeds development plans (M399) and the weighted
-- COMPETENCY section score (M394).

CREATE TABLE performance.perf_competency_assessment (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      varchar(64) NOT NULL DEFAULT 'default',
    review_id      uuid NOT NULL REFERENCES performance.performance_review (id) ON DELETE CASCADE,
    competency_id  uuid NOT NULL,
    required_level int,
    self_level     int,
    manager_level  int,
    final_level    int,
    gap            int,
    comment        varchar(1000),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT perf_comp_assessment_unique UNIQUE (review_id, competency_id),
    CONSTRAINT perf_comp_levels_check CHECK (
        (required_level IS NULL OR required_level BETWEEN 1 AND 5) AND
        (self_level     IS NULL OR self_level     BETWEEN 1 AND 5) AND
        (manager_level  IS NULL OR manager_level  BETWEEN 1 AND 5) AND
        (final_level    IS NULL OR final_level    BETWEEN 1 AND 5))
);
CREATE INDEX perf_comp_assessment_review_idx ON performance.perf_competency_assessment (review_id);

COMMENT ON TABLE performance.perf_competency_assessment IS
    'HCM_12 M393 — per-review competency assessment; gap = required − final (PRD §17.3).';
