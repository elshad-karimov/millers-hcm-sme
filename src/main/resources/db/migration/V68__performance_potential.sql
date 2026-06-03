-- M92 — Add potential rating + notes to performance_review for 9-box succession grid.
--
-- The 9-box succession matrix plots performance (existing final_rating) on one
-- axis and potential (new column) on the other. Both columns stay nullable
-- because old cycles won't have potential data; SuccessionPlanService
-- filters out rows missing either dimension.

ALTER TABLE performance.performance_review
    ADD COLUMN potential_rating numeric(4,2),
    ADD COLUMN potential_notes  text;

ALTER TABLE performance.performance_review
    ADD CONSTRAINT performance_review_potential_rating_range
    CHECK (potential_rating IS NULL OR (potential_rating >= 1 AND potential_rating <= 5));

COMMENT ON COLUMN performance.performance_review.potential_rating IS
    'M92 — Potential score 1-5 set during calibration. NULL until calibration adds it.';
COMMENT ON COLUMN performance.performance_review.potential_notes IS
    'M92 — Calibration rationale for the potential score.';
