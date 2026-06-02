-- M65 / Phase 1 — External certifications + medical/health records
-- (P1-13 + P1-14, CRITICAL)
--
-- Two compliance-driven tables:
--
--   * employee_certification — externally issued professional licences and
--     certifications (medical licence, driver's licence Class B, safety
--     certificate, PMP, etc.). Distinct from learning.certificate which only
--     tracks LMS course-completion certificates. Both can co-exist; this table
--     covers credentials granted by an external authority.
--
--   * employee_health — fitness certificates + periodic medical examinations.
--     Treated as GDPR Article 9 special-category data: stored in core_hr with
--     a dedicated CHECK column gating "confidential" rows. Application-level
--     role gate (OCCUPATIONAL_HEALTH / HR_ADMIN / SYSTEM_ADMIN) restricts
--     reads — this migration only ships the storage layer.
--
-- Both implement ExpiryTrackable and plug into the M61 ExpiryAlertScheduler
-- via two new ExpiryAlertSource beans — the scheduler picks them up
-- automatically (List<ExpiryAlertSource> auto-wired).

-- ── employee_certification ────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core_hr.employee_certification (
    id                       UUID         PRIMARY KEY,
    employee_id              UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    certification_name       VARCHAR(200) NOT NULL,
    issuing_authority        VARCHAR(200),
    -- Encrypted: enc:v1:<base64(IV||ciphertext||tag)>
    license_number           VARCHAR(500),
    issue_date               DATE,
    expiry_date              DATE,
    -- Soft FK — staffing.position lives in another schema and we never
    -- delete positions, so a hard FK adds friction without value.
    required_for_position_id UUID,
    verification_status      VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    verified_by              VARCHAR(80),
    verified_at              TIMESTAMPTZ,
    notes                    TEXT,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               VARCHAR(80),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by               VARCHAR(80),
    CONSTRAINT chk_emp_cert_verification
        CHECK (verification_status IN ('UNVERIFIED','VERIFIED','REJECTED')),
    CONSTRAINT chk_emp_cert_date_order
        CHECK (issue_date IS NULL OR expiry_date IS NULL OR issue_date <= expiry_date)
);

CREATE INDEX IF NOT EXISTS idx_emp_cert_employee
    ON core_hr.employee_certification (employee_id);

-- Hot path for the ExpiryAlertScheduler.
CREATE INDEX IF NOT EXISTS idx_emp_cert_expiry
    ON core_hr.employee_certification (expiry_date)
    WHERE expiry_date IS NOT NULL;


-- ── employee_health ───────────────────────────────────────────────────────
--
-- GDPR Article 9 special category data — application-level role gate
-- restricts reads to OCCUPATIONAL_HEALTH / HR_ADMIN / SYSTEM_ADMIN.

CREATE TABLE IF NOT EXISTS core_hr.employee_health (
    id                          UUID         PRIMARY KEY,
    employee_id                 UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    fitness_certificate_date    DATE,
    next_exam_date              DATE,
    -- Encrypted: enc:v1:<base64(IV||ciphertext||tag)> — medical notes are
    -- sensitive enough to encrypt at rest in addition to the role gate.
    occupational_health_notes   VARCHAR(4000),
    restrictions                TEXT,
    is_confidential             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                  VARCHAR(80),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by                  VARCHAR(80)
);

-- One health record per employee — keeps the model simple. Historical health
-- exams will eventually need their own child table; this row is the "current
-- state" reference.
CREATE UNIQUE INDEX IF NOT EXISTS uq_emp_health_one_per_employee
    ON core_hr.employee_health (employee_id);

CREATE INDEX IF NOT EXISTS idx_emp_health_next_exam
    ON core_hr.employee_health (next_exam_date)
    WHERE next_exam_date IS NOT NULL;
