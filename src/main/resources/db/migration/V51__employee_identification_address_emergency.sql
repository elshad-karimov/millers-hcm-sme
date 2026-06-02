-- M63 / Phase 1 — Identification documents, addresses, emergency contacts
-- (P1-04 + P1-06 + P1-07)
--
-- Three child tables hanging off core_hr.employee:
--
--   * employee_identification  — passport / visa / work permit / residency /
--                                driver license / national-id-as-document.
--                                Plugs into the M61 ExpiryAlertScheduler via
--                                an ExpiryAlertSource bean. Document numbers
--                                are AES-256-GCM encrypted in the same column
--                                pattern used by core_hr.employee.national_id.
--
--   * employee_address         — effective-dated address book. HOME / MAILING
--                                / WORK / EMERGENCY types. Uses the
--                                EffectiveDatedRecord pattern shipped in M62:
--                                at most one "open" (effective_to IS NULL)
--                                row per (employee, type) at any time, enforced
--                                by a partial unique index.
--
--   * employee_emergency_contact — flat list of next-of-kin contacts ordered
--                                by priority. No history — emergency contacts
--                                are point-in-time references.
--
-- All three accept attachments via the existing core_hr.attachment table
-- (ownerModule='CORE_HR', ownerEntity='EmployeeIdentification' /
--  'EmployeeAddress' / 'EmployeeEmergencyContact'). No new attachment schema.

-- ── employee_identification ───────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core_hr.employee_identification (
    id                  UUID         PRIMARY KEY,
    employee_id         UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    document_type       VARCHAR(20)  NOT NULL,
    -- Stored encrypted: enc:v1:<base64(IV||ciphertext||tag)> (~500 char ceiling).
    document_number     VARCHAR(500) NOT NULL,
    issue_date          DATE,
    expiry_date         DATE,
    issuing_authority   VARCHAR(160),
    -- ISO 3166-1 alpha-2 — matches employee.nationality convention.
    issuing_country     VARCHAR(2),
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    verified_by         VARCHAR(80),
    verified_at         TIMESTAMPTZ,
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(80),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          VARCHAR(80),
    CONSTRAINT chk_emp_id_document_type
        CHECK (document_type IN (
            'NATIONAL_ID','PASSPORT','VISA','WORK_PERMIT',
            'RESIDENCY_PERMIT','DRIVER_LICENSE'
        )),
    CONSTRAINT chk_emp_id_verification
        CHECK (verification_status IN ('UNVERIFIED','VERIFIED','REJECTED')),
    CONSTRAINT chk_emp_id_country_iso
        CHECK (issuing_country IS NULL
            OR (length(issuing_country) = 2 AND issuing_country = upper(issuing_country))),
    -- issue ≤ expiry when both are present
    CONSTRAINT chk_emp_id_date_order
        CHECK (issue_date IS NULL OR expiry_date IS NULL OR issue_date <= expiry_date)
);

CREATE INDEX IF NOT EXISTS idx_emp_id_employee
    ON core_hr.employee_identification (employee_id, document_type);

-- Hot path for ExpiryAlertScheduler.findExpiringOn(d). Excludes rows with
-- no expiry (e.g. a permanent national ID record).
CREATE INDEX IF NOT EXISTS idx_emp_id_expiry
    ON core_hr.employee_identification (expiry_date)
    WHERE expiry_date IS NOT NULL;


-- ── employee_address (effective-dated, EffectiveDatedRecord pattern) ──────

CREATE TABLE IF NOT EXISTS core_hr.employee_address (
    id                  UUID         PRIMARY KEY,
    employee_id         UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    address_type        VARCHAR(20)  NOT NULL,
    address_line1       VARCHAR(200) NOT NULL,
    address_line2       VARCHAR(200),
    city                VARCHAR(120),
    district            VARCHAR(120),
    -- ISO 3166-1 alpha-2.
    country             VARCHAR(2),
    postal_code         VARCHAR(20),
    effective_from      DATE         NOT NULL,
    effective_to        DATE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(80),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          VARCHAR(80),
    CONSTRAINT chk_emp_addr_type
        CHECK (address_type IN ('HOME','MAILING','WORK','EMERGENCY')),
    CONSTRAINT chk_emp_addr_country_iso
        CHECK (country IS NULL
            OR (length(country) = 2 AND country = upper(country))),
    CONSTRAINT chk_emp_addr_date_order
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX IF NOT EXISTS idx_emp_addr_employee_type_effective
    ON core_hr.employee_address (employee_id, address_type, effective_from DESC);

-- One open address per (employee, type) at any time — same invariant as
-- the M62 history tables, enforced at DB level.
CREATE UNIQUE INDEX IF NOT EXISTS uq_emp_addr_one_open_per_type
    ON core_hr.employee_address (employee_id, address_type)
    WHERE effective_to IS NULL;


-- ── employee_emergency_contact ────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core_hr.employee_emergency_contact (
    id                  UUID         PRIMARY KEY,
    employee_id         UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    name                VARCHAR(160) NOT NULL,
    relationship        VARCHAR(40)  NOT NULL,
    phone               VARCHAR(40)  NOT NULL,
    alt_phone           VARCHAR(40),
    email               VARCHAR(160),
    address             TEXT,
    is_primary          BOOLEAN      NOT NULL DEFAULT FALSE,
    priority_order      INTEGER      NOT NULL DEFAULT 100,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(80),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          VARCHAR(80),
    CONSTRAINT chk_emp_ec_relationship
        CHECK (relationship IN (
            'SPOUSE','CHILD','PARENT','SIBLING','GUARDIAN','FRIEND','OTHER'
        ))
);

CREATE INDEX IF NOT EXISTS idx_emp_ec_employee
    ON core_hr.employee_emergency_contact (employee_id, priority_order);

-- At most one PRIMARY emergency contact per employee. Same partial-unique-index
-- trick: PostgreSQL only enforces the constraint where the predicate matches.
CREATE UNIQUE INDEX IF NOT EXISTS uq_emp_ec_one_primary
    ON core_hr.employee_emergency_contact (employee_id)
    WHERE is_primary = TRUE;
