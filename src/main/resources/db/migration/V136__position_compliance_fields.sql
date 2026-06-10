-- ----------------------------------------------------------------------------
-- M254 — Phase G: PRD §44 Position Compliance Controls.
--
-- Adds the 7 compliance / regulatory fields the Position record needs
-- for government / regulated-industry deployments. AZ public-sector
-- employers in particular need to surface these on every position so
-- the establishment register matches the staffing table (M245).
--
--   establishment_number   — government registration / establishment id
--   civil_service_grade    — civil-service classification code
--   union_category         — union / labor-category code
--   exempt_status          — EXEMPT / NON_EXEMPT / SEMI_EXEMPT (overtime
--                            eligibility under labor act)
--   occupational_category  — ISCO / national occupational classification
--   labor_classification   — labor-act classification reference
--   legal_basis_reference  — appointment authority / legal act / order #
--
-- All nullable — purely additive, won't break existing positions.
-- ----------------------------------------------------------------------------

ALTER TABLE staffing.position
  ADD COLUMN IF NOT EXISTS establishment_number  VARCHAR(64),
  ADD COLUMN IF NOT EXISTS civil_service_grade   VARCHAR(32),
  ADD COLUMN IF NOT EXISTS union_category        VARCHAR(64),
  ADD COLUMN IF NOT EXISTS exempt_status         VARCHAR(16),
  ADD COLUMN IF NOT EXISTS occupational_category VARCHAR(32),
  ADD COLUMN IF NOT EXISTS labor_classification  VARCHAR(64),
  ADD COLUMN IF NOT EXISTS legal_basis_reference VARCHAR(200);

-- Index on establishment_number for fast lookup from the compliance
-- register report.
CREATE INDEX IF NOT EXISTS idx_position_establishment_no
    ON staffing.position(establishment_number)
    WHERE establishment_number IS NOT NULL;
