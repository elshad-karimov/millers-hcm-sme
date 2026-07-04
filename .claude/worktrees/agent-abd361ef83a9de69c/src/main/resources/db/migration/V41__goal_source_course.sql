-- M49: Link DEVELOPMENT performance goals to an LMS course (PRD 8.13 / 8.14).
-- When an enrollment for the linked course reaches PASSED, GoalAutoRatingService
-- auto-rates all open DEVELOPMENT goals that carry this FK.

ALTER TABLE performance.goal
    ADD COLUMN IF NOT EXISTS source_course_id UUID
        REFERENCES learning.course(id);

CREATE INDEX IF NOT EXISTS idx_goal_source_course_id
    ON performance.goal(source_course_id)
    WHERE source_course_id IS NOT NULL;

COMMENT ON COLUMN performance.goal.source_course_id IS
    'Optional FK to the LMS course that triggers auto-rating when passed (M49).
     NULL = manual rating only.  Auto-rating computes: rating = min(5, bestScore%/20);
     status = ACHIEVED if rating >= 3, MISSED otherwise.';
