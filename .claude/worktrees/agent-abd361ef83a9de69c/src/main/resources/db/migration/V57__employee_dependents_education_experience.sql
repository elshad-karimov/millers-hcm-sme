-- M71 / Phase 2 — Dependents + Education + Work Experience
-- (P2-03 + P2-04 + P2-05, HIGH priority)
--
-- Three more child tables on core_hr.employee, completing the personal-profile
-- coverage:
--
--   * employee_dependent       — spouse / children / parents / siblings.
--     Drives insurance eligibility, family-allowance lookups, parental-leave
--     entitlement (LeaveAccrualService can consult dependent count once the
--     leave types that need it are configured).
--
--   * employee_education       — academic history. CV-style entries from
--     high school through doctorate, with verification status mirroring
--     EmployeeIdentification's UNVERIFIED → VERIFIED / REJECTED flow.
--
--   * employee_work_experience — career history. Distinguishes EXTERNAL
--     (prior employers) from INTERNAL (recorded role inside this company).
--     last_salary is AES-256-GCM encrypted to match the existing PII
--     convention on Employee.nationalId.
--
-- All three accept attachments via the existing core_hr.attachment table —
-- no new attachment schema.

-- ── employee_dependent ────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core_hr.employee_dependent (
    id                  UUID         PRIMARY KEY,
    employee_id         UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    relationship_type   VARCHAR(20)  NOT NULL,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    middle_name         VARCHAR(100),
    date_of_birth       DATE,
    gender              VARCHAR(16),
    -- Encrypted via the same EncryptedStringConverter pattern as
    -- Employee.nationalId.
    national_id         VARCHAR(500),
    phone               VARCHAR(40),
    email               VARCHAR(160),
    -- Drives benefit enrolment + family allowance lookups.
    insurance_eligible  BOOLEAN      NOT NULL DEFAULT FALSE,
    benefit_eligible    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(80),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          VARCHAR(80),
    CONSTRAINT chk_dep_relationship CHECK (relationship_type IN (
        'SPOUSE','CHILD','PARENT','SIBLING','GUARDIAN','OTHER'
    ))
);

CREATE INDEX IF NOT EXISTS idx_dep_employee
    ON core_hr.employee_dependent (employee_id, is_active);


-- ── employee_education ────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core_hr.employee_education (
    id                  UUID         PRIMARY KEY,
    employee_id         UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    education_level     VARCHAR(30)  NOT NULL,
    institution_name    VARCHAR(200) NOT NULL,
    country             VARCHAR(2),
    degree              VARCHAR(200),
    major               VARCHAR(200),
    start_date          DATE,
    end_date            DATE,
    gpa                 NUMERIC(4,2),
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    verified_by         VARCHAR(80),
    verified_at         TIMESTAMPTZ,
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(80),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          VARCHAR(80),
    CONSTRAINT chk_edu_level CHECK (education_level IN (
        'HIGH_SCHOOL','DIPLOMA','VOCATIONAL','ASSOCIATE',
        'BACHELOR','MASTER','DOCTORATE','PROFESSIONAL','OTHER'
    )),
    CONSTRAINT chk_edu_verification CHECK (verification_status IN ('UNVERIFIED','VERIFIED','REJECTED')),
    CONSTRAINT chk_edu_country_iso CHECK (
        country IS NULL OR (length(country) = 2 AND country = upper(country))
    ),
    CONSTRAINT chk_edu_dates CHECK (
        start_date IS NULL OR end_date IS NULL OR start_date <= end_date
    ),
    CONSTRAINT chk_edu_gpa CHECK (
        gpa IS NULL OR (gpa >= 0 AND gpa <= 5)
    )
);

CREATE INDEX IF NOT EXISTS idx_edu_employee
    ON core_hr.employee_education (employee_id, end_date DESC NULLS FIRST);


-- ── employee_work_experience ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core_hr.employee_work_experience (
    id                  UUID         PRIMARY KEY,
    employee_id         UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    experience_type     VARCHAR(20)  NOT NULL DEFAULT 'EXTERNAL',
    employer_name       VARCHAR(200) NOT NULL,
    industry            VARCHAR(120),
    job_title           VARCHAR(200) NOT NULL,
    start_date          DATE         NOT NULL,
    end_date            DATE,
    reason_for_leaving  VARCHAR(200),
    -- Encrypted: enc:v1:<base64(IV||ciphertext||tag)>.
    last_salary         VARCHAR(500),
    last_salary_currency VARCHAR(3),
    responsibilities    TEXT,
    reference_contact   VARCHAR(200),
    reference_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    verified_by         VARCHAR(80),
    verified_at         TIMESTAMPTZ,
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(80),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          VARCHAR(80),
    CONSTRAINT chk_exp_type CHECK (experience_type IN ('EXTERNAL','INTERNAL')),
    CONSTRAINT chk_exp_verification CHECK (verification_status IN ('UNVERIFIED','VERIFIED','REJECTED')),
    CONSTRAINT chk_exp_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX IF NOT EXISTS idx_exp_employee
    ON core_hr.employee_work_experience (employee_id, start_date DESC);
