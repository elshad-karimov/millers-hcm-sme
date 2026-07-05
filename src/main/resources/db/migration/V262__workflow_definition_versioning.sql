-- V262: M442 workflow definition versioning

-- Add versioning columns to workflow_definition
ALTER TABLE workflow.workflow_definition
ADD COLUMN version INT NOT NULL DEFAULT 1,
ADD COLUMN effective_from DATE,
ADD COLUMN effective_to DATE;

-- Update existing unique constraint on code to include version
-- First, find and drop the existing constraint
ALTER TABLE workflow.workflow_definition
DROP CONSTRAINT IF EXISTS workflow_definition_code_key,
DROP CONSTRAINT IF EXISTS uq_workflow_definition_code;

-- Add new unique constraint on (code, version)
ALTER TABLE workflow.workflow_definition
ADD CONSTRAINT uq_workflow_definition_code_version UNIQUE (code, version);

-- Create index for version window queries
CREATE INDEX idx_workflow_definition_effective ON workflow.workflow_definition(code, effective_from, effective_to) WHERE active = true;

COMMENT ON COLUMN workflow.workflow_definition.version IS 'M442 — Version number (1-based); effective window resolves which version applies';
COMMENT ON COLUMN workflow.workflow_definition.effective_from IS 'Start date for this version (NULL = always valid)';
COMMENT ON COLUMN workflow.workflow_definition.effective_to IS 'End date for this version (NULL = no end)';
