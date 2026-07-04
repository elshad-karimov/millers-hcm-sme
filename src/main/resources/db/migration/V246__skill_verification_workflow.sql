-- M419: Skill verification workflow
-- Employee submits request to verify their competency level, manager/HR approves

CREATE TABLE learning.skill_verification_request (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    employee_id UUID NOT NULL,
    competency_id UUID NOT NULL,
    requested_level INT NOT NULL CHECK (requested_level BETWEEN 1 AND 5),
    evidence_notes TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED')),

    verified_by_employee_id UUID,
    verified_at TIMESTAMPTZ,
    verification_notes TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_skill_verification_employee
        FOREIGN KEY (employee_id) REFERENCES core_hr.employee(id),
    CONSTRAINT fk_skill_verification_competency
        FOREIGN KEY (competency_id) REFERENCES learning.competency(id),
    CONSTRAINT fk_skill_verification_verifier
        FOREIGN KEY (verified_by_employee_id) REFERENCES core_hr.employee(id)
);

CREATE INDEX idx_skill_verification_employee ON learning.skill_verification_request(employee_id, tenant_id);
CREATE INDEX idx_skill_verification_status ON learning.skill_verification_request(status, tenant_id);
CREATE INDEX idx_skill_verification_competency ON learning.skill_verification_request(competency_id, tenant_id);

COMMENT ON TABLE learning.skill_verification_request IS 'M419: Employee-initiated skill verification workflow';
