-- HCM_12 Performance Management — M394 (Phase B.3)
-- Weighted section scoring (PRD §18.2) + score→band conversion (§18.3) + manual
-- override (§18.4). goal_score already exists (V19); this adds the remaining
-- section scores, the computed overall, and the override trail. The original
-- computed rating is PRESERVED on override — never overwritten.

ALTER TABLE performance.performance_review
    ADD COLUMN kpi_score        numeric(6,3),
    ADD COLUMN competency_score numeric(6,3),
    ADD COLUMN values_score     numeric(6,3),
    ADD COLUMN overall_score    numeric(6,3),
    ADD COLUMN overall_band     varchar(120),
    ADD COLUMN original_rating  numeric(6,3),
    ADD COLUMN override_reason  varchar(1000),
    ADD COLUMN overridden_by    varchar(80),
    ADD COLUMN overridden_at    timestamptz;

COMMENT ON COLUMN performance.performance_review.overall_score IS
    'HCM_12 M394 — §18.2 weighted overall: Σ(section score × template weight) / Σ(participating weights), 1..5.';
COMMENT ON COLUMN performance.performance_review.original_rating IS
    'HCM_12 M394 — §18.4 pre-override computed rating, preserved forever once an override happens.';
