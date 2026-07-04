-- HCM_16 M416 — career paths (PRD §16.6)
-- Maps progression routes between positions (e.g. Analyst → Senior Analyst →
-- Manager) with required skills, certs, experience, courses, tenure. Employees
-- can see transparent paths; HR manages them.

CREATE TABLE IF NOT EXISTS staffing.career_path (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    code            VARCHAR(32) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    job_family      VARCHAR(100),
    description     TEXT,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_by      VARCHAR(80),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS staffing.career_path_step (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    path_id         UUID NOT NULL REFERENCES staffing.career_path (id) ON DELETE CASCADE,
    step_order      INT NOT NULL,
    from_position_id UUID,  -- NULL for entry step
    to_position_id  UUID NOT NULL,  -- FK staffing.position
    required_skills VARCHAR(1000),
    required_certifications VARCHAR(500),
    required_experience_years INT,
    required_courses VARCHAR(500),
    typical_tenure_months INT,
    UNIQUE (path_id, step_order)
);

CREATE INDEX idx_career_path_tenant ON staffing.career_path (tenant_id, active);
CREATE INDEX idx_career_path_step_path ON staffing.career_path_step (path_id);

COMMENT ON TABLE staffing.career_path IS 'HCM_16 M416 — career progression paths (PRD §16.6)';
COMMENT ON TABLE staffing.career_path_step IS 'HCM_16 M416 — steps in a career path';
