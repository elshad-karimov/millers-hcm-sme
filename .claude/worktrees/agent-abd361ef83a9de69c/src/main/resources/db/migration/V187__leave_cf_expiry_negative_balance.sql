-- M340: Carry-forward expiry window + negative-balance policy on leave types.
-- carry_forward_expiry_months: months from Jan 1 of the new year before the
--   carried-forward days expire (NULL = never expire mid-year; they already
--   expire at the next year-end roll-over per carry_forward_limit_days logic).
-- negative_balance_allowed: permits requests beyond the current balance.
-- max_negative_days: caps the overdraft (NULL = unlimited when allowed).
-- carry_forward_expires_at on leave_balance: the concrete expiry date written
--   at roll-over time so the daily sweep can act on it without recomputing.

ALTER TABLE leave_mgmt.leave_type
    ADD COLUMN carry_forward_expiry_months INT,
    ADD COLUMN negative_balance_allowed    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN max_negative_days           NUMERIC(5, 2);

ALTER TABLE leave_mgmt.leave_balance
    ADD COLUMN carry_forward_expires_at DATE;

CREATE INDEX idx_leave_balance_cf_expiry
    ON leave_mgmt.leave_balance (carry_forward_expires_at)
    WHERE carry_forward_expires_at IS NOT NULL;
