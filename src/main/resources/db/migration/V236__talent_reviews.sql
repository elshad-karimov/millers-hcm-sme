-- HCM_15 M411 — talent review cycles + talent reviews (PRD §15.7/§15.8).
-- Panel decisions: 9-box placement, HiPo designation, retention risk + action.
-- Decisions roll onto talent_profile (M409). HR-only (GLOBAL RULE 17).

CREATE TABLE IF NOT EXISTS performance.talent_review_cycle (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    name            VARCHAR(200) NOT NULL,
    year            INT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT | ACTIVE | COMPLETED
    created_by      VARCHAR(80),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tr_cycle_status CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED'))
);
CREATE INDEX idx_tr_cycle_tenant ON performance.talent_review_cycle (tenant_id, status);

CREATE TABLE IF NOT EXISTS performance.talent_review (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    cycle_id        UUID NOT NULL REFERENCES performance.talent_review_cycle (id),
    employee_id     UUID NOT NULL,

    -- 9-box placement (performance=1..3, potential=1..3 → 1=low, 3=high)
    performance_box INT,                 -- nullable: may not be placed yet
    potential_box   INT,

    -- HiPo + retention decision
    hipo_decision   BOOLEAN NOT NULL DEFAULT false,
    retention_risk  VARCHAR(10),         -- LOW | MEDIUM | HIGH | CRITICAL
    retention_action VARCHAR(2000),

    notes           VARCHAR(2000),
    decided_by      VARCHAR(80),
    decided_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_tr_cycle_emp UNIQUE (cycle_id, employee_id),
    CONSTRAINT chk_tr_perf_box CHECK (performance_box BETWEEN 1 AND 3),
    CONSTRAINT chk_tr_pot_box CHECK (potential_box BETWEEN 1 AND 3),
    CONSTRAINT chk_tr_risk CHECK (retention_risk IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);
CREATE INDEX idx_tr_tenant ON performance.talent_review (tenant_id);
CREATE INDEX idx_tr_employee ON performance.talent_review (employee_id);
CREATE INDEX idx_tr_hipo ON performance.talent_review (cycle_id, hipo_decision);
CREATE INDEX idx_tr_risk ON performance.talent_review (cycle_id, retention_risk);

COMMENT ON TABLE performance.talent_review_cycle IS 'HCM_15 M411 — talent review cycles (PRD §15.7)';
COMMENT ON TABLE performance.talent_review IS 'HCM_15 M411 — talent review decisions (PRD §15.8)';
COMMENT ON COLUMN performance.talent_review.performance_box IS '1=low, 2=mid, 3=high performance';
COMMENT ON COLUMN performance.talent_review.potential_box IS '1=low, 2=mid, 3=high potential';
