-- HCM_16 M414 — succession nomination risk/impact fields (PRD §16 enrichment)
-- Extends M103/V70 performance.succession_nomination: add risk_of_loss (flight
-- risk), impact_of_loss (business impact if nominee leaves), risk_reason, and
-- retention_action for high-value/high-risk successors.

ALTER TABLE performance.succession_nomination
    ADD COLUMN IF NOT EXISTS risk_of_loss VARCHAR(10),
    ADD COLUMN IF NOT EXISTS impact_of_loss VARCHAR(10),
    ADD COLUMN IF NOT EXISTS risk_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS retention_action VARCHAR(1000),
    ADD CONSTRAINT chk_nom_risk CHECK (
        risk_of_loss IS NULL OR risk_of_loss IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    ADD CONSTRAINT chk_nom_impact CHECK (
        impact_of_loss IS NULL OR impact_of_loss IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    );

COMMENT ON COLUMN performance.succession_nomination.risk_of_loss IS 'Flight risk — likelihood nominee leaves (LOW|MEDIUM|HIGH|CRITICAL)';
COMMENT ON COLUMN performance.succession_nomination.impact_of_loss IS 'Business impact if nominee leaves (LOW|MEDIUM|HIGH|CRITICAL)';
