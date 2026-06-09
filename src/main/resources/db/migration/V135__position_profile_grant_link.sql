-- ----------------------------------------------------------------------------
-- M251 — Phase F.3: back-link from grant to the downstream entity it
-- created. Currently only used for ALLOWANCE grants (linked to
-- comp_benefits.employee_allowance) but reserved as a generic UUID so
-- later phases can wire TRAINING (→ learning.enrollment), EQUIPMENT
-- (→ asset.issuance), etc. through the same column without another
-- migration.
-- ----------------------------------------------------------------------------

ALTER TABLE staffing.position_profile_grant
  ADD COLUMN IF NOT EXISTS downstream_entity_id   UUID,
  ADD COLUMN IF NOT EXISTS downstream_entity_type VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_grant_downstream
    ON staffing.position_profile_grant(downstream_entity_type, downstream_entity_id);
