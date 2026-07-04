-- M320: Link exit_interview to offboarding_case (§17)
-- Make termination_id nullable so resignation-driven cases can also have an exit interview
ALTER TABLE lifecycle.exit_interview
    ALTER COLUMN termination_id DROP NOT NULL;

ALTER TABLE lifecycle.exit_interview
    ADD COLUMN IF NOT EXISTS case_id UUID REFERENCES lifecycle.offboarding_case(id),
    ADD COLUMN IF NOT EXISTS interview_mode VARCHAR(30),
    ADD COLUMN IF NOT EXISTS interview_date DATE,
    ADD COLUMN IF NOT EXISTS follow_up_required BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS follow_up_notes TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_exit_interview_case_id
    ON lifecycle.exit_interview (case_id)
    WHERE case_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_exit_interview_case_id
    ON lifecycle.exit_interview (case_id);
