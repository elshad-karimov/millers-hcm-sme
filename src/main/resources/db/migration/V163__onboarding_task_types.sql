-- ----------------------------------------------------------------------------
-- M299 — Onboarding PRD Phase A.2: typed onboarding tasks + categories.
--
-- Each checklist task gains a task_type (what the task is — document, contract,
-- equipment, IT account, training, …; PRD §2 task types) and an optional
-- category (which onboarding stream it belongs to — HR / IT / Facilities /
-- First-day / …; PRD §2 categories). task_type drives the default owner role
-- at start time and, from Phase B onward, the downstream action the task
-- triggers. Both are copied onto the per-task status snapshot so the persona
-- dashboards (Phase E) can group running tasks without joining back to the
-- template.
--
-- Stored as plain VARCHAR (no CHECK) so the enum lists can grow without a
-- migration — the application validates via @Enumerated. Default MANUAL_TASK
-- keeps every pre-M299 template task valid.
-- ----------------------------------------------------------------------------

ALTER TABLE lifecycle.checklist_template_task
    ADD COLUMN IF NOT EXISTS task_type varchar(40) NOT NULL DEFAULT 'MANUAL_TASK',
    ADD COLUMN IF NOT EXISTS category  varchar(40);

ALTER TABLE lifecycle.checklist_task_status
    ADD COLUMN IF NOT EXISTS task_type varchar(40) NOT NULL DEFAULT 'MANUAL_TASK',
    ADD COLUMN IF NOT EXISTS category  varchar(40);

COMMENT ON COLUMN lifecycle.checklist_template_task.task_type IS
    'M299 — ChecklistTaskType: classifies the task (drives default owner + Phase B downstream action).';
COMMENT ON COLUMN lifecycle.checklist_template_task.category IS
    'M299 — ChecklistOnboardingCategory: onboarding stream/persona grouping (nullable).';
