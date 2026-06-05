-- M122 — Pre-boarding portal Phase 1.
--
-- HR issues a magic-link invite to a newly-hired employee whose Employee
-- record exists (created by the recruitment HIRED handoff) but whose
-- personal data is still mostly null. The candidate fills in their info
-- via /preboarding/{token} (a public SPA route), HR reviews the payload,
-- and on Complete the data is graduated into Employee + EmergencyContact
-- + Dependent rows.
--
-- Token model mirrors M120 API keys: plaintext is generated server-side,
-- shown once, and stored only as a SHA-256 hash. A leaked dump can't be
-- replayed to impersonate the candidate.

CREATE TABLE core_hr.preboarding_invite (
    id              UUID         PRIMARY KEY,
    employee_id     UUID         NOT NULL REFERENCES core_hr.employee(id) ON DELETE CASCADE,

    -- SHA-256 of the plaintext token. UNIQUE so two issued tokens can never
    -- collide and an attacker who reads a DB row still can't authenticate.
    token_hash      CHAR(64)     NOT NULL UNIQUE,

    -- DRAFT, SENT, OPENED, SUBMITTED, COMPLETED, EXPIRED, REVOKED
    status          VARCHAR(24)  NOT NULL DEFAULT 'DRAFT',

    expires_at      TIMESTAMPTZ  NOT NULL,
    sent_at         TIMESTAMPTZ,
    opened_at       TIMESTAMPTZ,
    submitted_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    completed_by    VARCHAR(160),
    revoked_at      TIMESTAMPTZ,
    revoked_by      VARCHAR(160),
    revoke_reason   TEXT,

    -- Candidate-submitted form data. Validated on submit() against the
    -- schema in PreboardingFormSchema. Read by complete() to graduate
    -- into Employee + EmergencyContact + Dependent rows.
    payload_json    JSONB,

    note            TEXT,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(160) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_preboarding_employee ON core_hr.preboarding_invite (employee_id);
CREATE INDEX ix_preboarding_status   ON core_hr.preboarding_invite (status);
-- HR's "outstanding invites" widget — partial index keeps it cheap.
CREATE INDEX ix_preboarding_open
    ON core_hr.preboarding_invite (expires_at)
    WHERE status IN ('SENT', 'OPENED', 'SUBMITTED');
