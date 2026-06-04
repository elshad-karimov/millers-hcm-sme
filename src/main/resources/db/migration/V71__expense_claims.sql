-- M104 — Travel expense claims.
--
-- An expense claim records the actual costs incurred during a business trip,
-- submitted by the employee for reimbursement after return. Each claim has
-- one or more line items (expense_item rows); the claim total is derived
-- from the items.

CREATE SEQUENCE business_trip.claim_no_seq START 1000;

CREATE TABLE business_trip.expense_claim (
    id              uuid PRIMARY KEY,
    claim_no        varchar(20) NOT NULL UNIQUE DEFAULT 'EXP-' || nextval('business_trip.claim_no_seq'),
    trip_id         uuid NOT NULL REFERENCES business_trip.business_trip_request(id),
    employee_id     uuid NOT NULL,
    currency        varchar(10) NOT NULL DEFAULT 'AZN',
    status          varchar(20) NOT NULL DEFAULT 'DRAFT',
    submitted_at    timestamptz,
    approved_at     timestamptz,
    approved_by     varchar(80),
    rejected_at     timestamptz,
    rejection_reason text,
    paid_at         timestamptz,
    notes           text,
    created_by      varchar(80),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT expense_claim_status_check
        CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','PAID'))
);

-- At most one active (non-rejected, non-draft) claim per trip.
CREATE UNIQUE INDEX expense_claim_one_active_per_trip
    ON business_trip.expense_claim (trip_id)
    WHERE status IN ('SUBMITTED','APPROVED','PAID');

CREATE INDEX expense_claim_employee_idx ON business_trip.expense_claim (employee_id, status);
CREATE INDEX expense_claim_trip_idx     ON business_trip.expense_claim (trip_id);

-- Expense line items.
CREATE TABLE business_trip.expense_item (
    id          uuid PRIMARY KEY,
    claim_id    uuid NOT NULL REFERENCES business_trip.expense_claim(id) ON DELETE CASCADE,
    category    varchar(40) NOT NULL,
    description text,
    amount      numeric(14,2) NOT NULL,
    item_date   date,
    receipt_url text,
    CONSTRAINT expense_item_amount_positive CHECK (amount >= 0),
    CONSTRAINT expense_item_category_check
        CHECK (category IN (
            'ACCOMMODATION','MEALS','TRANSPORT','FLIGHT','VISA_FEES',
            'COMMUNICATION','REGISTRATION','OTHER'))
);

CREATE INDEX expense_item_claim_idx ON business_trip.expense_item (claim_id);

COMMENT ON TABLE business_trip.expense_claim IS
    'M104 — Post-trip expense claim submitted by the employee for reimbursement.';
COMMENT ON TABLE business_trip.expense_item IS
    'M104 — Individual line items on an expense claim.';
