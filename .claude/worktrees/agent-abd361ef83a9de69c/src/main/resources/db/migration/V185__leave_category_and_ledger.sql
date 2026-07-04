-- M338: Leave categories + balance ledger

CREATE TABLE leave_mgmt.leave_category (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(40)  NOT NULL UNIQUE,
    name            VARCHAR(160) NOT NULL,
    description     TEXT,
    paid_default    BOOLEAN      NOT NULL DEFAULT TRUE,
    reporting_group VARCHAR(64),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(120),
    updated_by      VARCHAR(120)
);

INSERT INTO leave_mgmt.leave_category (code, name, paid_default, reporting_group, created_by)
VALUES
  ('PAID', 'Paid Leave', TRUE, 'PAID', 'system'),
  ('UNPAID', 'Unpaid Leave', FALSE, 'UNPAID', 'system'),
  ('MEDICAL', 'Medical Leave', TRUE, 'MEDICAL', 'system'),
  ('STATUTORY', 'Statutory Leave', TRUE, 'STATUTORY', 'system'),
  ('FAMILY', 'Family Leave', TRUE, 'FAMILY', 'system'),
  ('EDUCATIONAL', 'Educational Leave', TRUE, 'EDUCATIONAL', 'system'),
  ('BUSINESS', 'Business Absence', TRUE, 'BUSINESS', 'system');

ALTER TABLE leave_mgmt.leave_type
    ADD COLUMN category_id UUID REFERENCES leave_mgmt.leave_category(id);

UPDATE leave_mgmt.leave_type SET category_id = (SELECT id FROM leave_mgmt.leave_category WHERE code='PAID') WHERE code='ANNUAL';
UPDATE leave_mgmt.leave_type SET category_id = (SELECT id FROM leave_mgmt.leave_category WHERE code='MEDICAL') WHERE code='SICK';
UPDATE leave_mgmt.leave_type SET category_id = (SELECT id FROM leave_mgmt.leave_category WHERE code='STATUTORY') WHERE code='MATERNITY';
UPDATE leave_mgmt.leave_type SET category_id = (SELECT id FROM leave_mgmt.leave_category WHERE code='STATUTORY') WHERE code='PATERNITY';
UPDATE leave_mgmt.leave_type SET category_id = (SELECT id FROM leave_mgmt.leave_category WHERE code='PAID') WHERE code='MARRIAGE';
UPDATE leave_mgmt.leave_type SET category_id = (SELECT id FROM leave_mgmt.leave_category WHERE code='PAID') WHERE code='COMPASSIONATE';
UPDATE leave_mgmt.leave_type SET category_id = (SELECT id FROM leave_mgmt.leave_category WHERE code='EDUCATIONAL') WHERE code='EDUCATIONAL';
UPDATE leave_mgmt.leave_type SET category_id = (SELECT id FROM leave_mgmt.leave_category WHERE code='UNPAID') WHERE code='UNPAID';

CREATE TABLE leave_mgmt.leave_balance_ledger (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id         UUID         NOT NULL,
    leave_type_id       UUID         NOT NULL REFERENCES leave_mgmt.leave_type(id),
    year                INTEGER      NOT NULL,
    tx_type             VARCHAR(32)  NOT NULL,
    amount              NUMERIC(7,2) NOT NULL,
    effective_date      DATE         NOT NULL,
    source_type         VARCHAR(32),
    source_id           VARCHAR(120),
    balance_after       NUMERIC(7,2) NOT NULL,
    notes               TEXT,
    created_by          VARCHAR(120),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_ledger_tx_type CHECK (tx_type IN (
        'OPENING','ACCRUAL','LEAVE_TAKEN','LEAVE_CANCELLED','ADJUSTMENT',
        'CARRY_FORWARD','EXPIRY','ENCASHMENT','PAYROLL_CORRECTION','RESERVATION','RESERVATION_RELEASE'
    ))
);

CREATE INDEX idx_ledger_emp_type_year ON leave_mgmt.leave_balance_ledger (employee_id, leave_type_id, year);
CREATE INDEX idx_ledger_emp_date ON leave_mgmt.leave_balance_ledger (employee_id, effective_date DESC);
