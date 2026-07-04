-- M185: Track when Keycloak access was revoked for terminated employees.
-- A NULL value means the scheduler has not yet disabled the account;
-- once the revocation job runs at EOD of the effective date it sets this timestamp.

ALTER TABLE lifecycle.termination_request
    ADD COLUMN system_access_revoked_at TIMESTAMPTZ;

-- Index for the scheduler: quickly find PROCESSED terminations whose
-- effective date has passed but whose access has not yet been revoked.
CREATE INDEX idx_term_access_revoke
    ON lifecycle.termination_request (effective_date)
    WHERE status = 'PROCESSED'
      AND system_access_revoked_at IS NULL;
