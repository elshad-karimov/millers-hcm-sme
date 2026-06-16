-- ----------------------------------------------------------------------------
-- M303 — Onboarding PRD Phase B.3: training onboarding task → LMS enrol /
-- learning-path assign.
--
-- A TRAINING_ASSIGNMENT checklist task can now point at an LMS course or a
-- learning path. When onboarding starts, the OnboardingTrainingService enrols
-- the new hire in the course (M20) or assigns the path (M95). When the course
-- is later passed (CoursePassedEvent) the originating checklist task is marked
-- DONE — closing the loop assign → complete.
--
-- target_kind COURSE → target_id = learning.course.id
-- target_kind PATH   → target_id = learning.learning_path.id
-- Plain VARCHAR (no CHECK) for cheap enum evolution; app validates.
-- ----------------------------------------------------------------------------

ALTER TABLE lifecycle.checklist_template_task
    ADD COLUMN IF NOT EXISTS training_target_kind varchar(8),
    ADD COLUMN IF NOT EXISTS training_target_id   uuid;

ALTER TABLE lifecycle.checklist_task_status
    ADD COLUMN IF NOT EXISTS training_target_kind varchar(8),
    ADD COLUMN IF NOT EXISTS training_target_id   uuid;

COMMENT ON COLUMN lifecycle.checklist_template_task.training_target_kind IS
    'M303 — TrainingTargetKind COURSE|PATH for TRAINING_ASSIGNMENT tasks; target_id points at the LMS course or learning path.';
