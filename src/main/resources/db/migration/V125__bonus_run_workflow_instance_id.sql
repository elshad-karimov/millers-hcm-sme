-- Add workflow_instance_id to comp_benefits.bonus_run (missing column added by entity).
ALTER TABLE comp_benefits.bonus_run
    ADD COLUMN IF NOT EXISTS workflow_instance_id uuid;
