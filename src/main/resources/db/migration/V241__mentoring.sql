-- HCM_16 M417 — mentoring (PRD §16.7)
-- Mentor profiles + mentoring relationships. Mentees request; mentor/HR approve.
-- Active mentor-mentee pairs exchange meeting notes. Confidentiality: mentor+
-- mentee+HR see the relationship, others don't.

CREATE TABLE IF NOT EXISTS performance.mentor_profile (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    employee_id     UUID NOT NULL,  -- FK core_hr.employee
    areas           VARCHAR(500),  -- mentoring areas of expertise
    max_mentees     INT NOT NULL DEFAULT 3,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, employee_id)
);

CREATE TABLE IF NOT EXISTS performance.mentoring_relationship (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    mentor_employee_id UUID NOT NULL,  -- FK core_hr.employee
    mentee_employee_id UUID NOT NULL,  -- FK core_hr.employee
    start_date      DATE NOT NULL,
    end_date        DATE,
    status          VARCHAR(12) NOT NULL DEFAULT 'REQUESTED',
        -- REQUESTED | ACTIVE | COMPLETED | CANCELLED
    meeting_notes   VARCHAR(2000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ment_status CHECK (status IN ('REQUESTED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_ment_diff CHECK (mentor_employee_id <> mentee_employee_id)
);

CREATE INDEX idx_mentor_profile_tenant ON performance.mentor_profile (tenant_id, active);
CREATE INDEX idx_ment_rel_tenant ON performance.mentoring_relationship (tenant_id, status);
CREATE INDEX idx_ment_rel_mentor ON performance.mentoring_relationship (mentor_employee_id);
CREATE INDEX idx_ment_rel_mentee ON performance.mentoring_relationship (mentee_employee_id);

COMMENT ON TABLE performance.mentor_profile IS 'HCM_16 M417 — mentor profiles (PRD §16.7)';
COMMENT ON TABLE performance.mentoring_relationship IS 'HCM_16 M417 — mentor-mentee relationships';
