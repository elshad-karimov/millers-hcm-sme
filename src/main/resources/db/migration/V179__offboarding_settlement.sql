-- M321: Final settlement workbench (Offboarding PRD §18-§22)
CREATE TABLE IF NOT EXISTS lifecycle.offboarding_settlement (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id          UUID         NOT NULL UNIQUE REFERENCES lifecycle.offboarding_case(id),
    settlement_no    VARCHAR(20)  NOT NULL UNIQUE
                                  DEFAULT 'SETT-' || nextval('lifecycle.offboarding_case_no_seq')::TEXT,
    status           VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    currency         VARCHAR(10)  NOT NULL DEFAULT 'AZN',
    total_gross      NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_deductions NUMERIC(14,2) NOT NULL DEFAULT 0,
    net_payable      NUMERIC(14,2) NOT NULL DEFAULT 0,
    payment_date     DATE,
    payment_method   VARCHAR(40),
    bank_reference   VARCHAR(120),
    notes            TEXT,
    prepared_by      VARCHAR(100),
    approved_by      VARCHAR(100),
    approved_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS lifecycle.offboarding_settlement_component (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id   UUID          NOT NULL REFERENCES lifecycle.offboarding_settlement(id) ON DELETE CASCADE,
    component_type  VARCHAR(50)   NOT NULL,
    description     VARCHAR(200)  NOT NULL,
    amount          NUMERIC(14,2) NOT NULL DEFAULT 0,
    is_deduction    BOOLEAN       NOT NULL DEFAULT false,
    calculation_basis TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_settlement_case_id
    ON lifecycle.offboarding_settlement (case_id);
CREATE INDEX IF NOT EXISTS idx_settlement_component_settlement_id
    ON lifecycle.offboarding_settlement_component (settlement_id);
