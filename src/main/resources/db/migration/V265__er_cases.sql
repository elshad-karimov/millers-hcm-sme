-- V265: M445 ER cases — CONFIDENTIAL employee relations module

CREATE SCHEMA IF NOT EXISTS employee_relations;

-- ER case types and statuses
-- No CHECK constraints (VARCHAR for flexibility)

CREATE SEQUENCE employee_relations.er_case_no_seq START WITH 1;

CREATE TABLE employee_relations.er_case (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    case_no VARCHAR(20) NOT NULL UNIQUE,
    employee_id UUID, -- nullable for anonymous complaints
    case_type VARCHAR(60) NOT NULL, -- GRIEVANCE, COMPLAINT, INVESTIGATION, DISCIPLINARY_LINK
    category VARCHAR(60),
    severity VARCHAR(20) NOT NULL, -- LOW, MEDIUM, HIGH, CRITICAL
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN', -- OPEN, UNDER_INVESTIGATION, RESOLVED, CLOSED
    owner_username VARCHAR(255),

    is_confidential BOOLEAN NOT NULL DEFAULT FALSE,
    legal_hold BOOLEAN NOT NULL DEFAULT FALSE,
    linked_disciplinary_id UUID,

    description TEXT,
    outcome TEXT,
    closed_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(255)
);

CREATE TABLE employee_relations.er_case_note (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    case_id UUID NOT NULL,
    is_internal BOOLEAN NOT NULL DEFAULT FALSE,
    body TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL,

    CONSTRAINT fk_er_note_case FOREIGN KEY (case_id) REFERENCES employee_relations.er_case(id) ON DELETE CASCADE
);

CREATE TABLE employee_relations.er_investigation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    case_id UUID NOT NULL,
    investigator_username VARCHAR(255) NOT NULL,
    findings TEXT,
    recommendation TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN, CLOSED
    closed_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL,

    CONSTRAINT fk_er_investigation_case FOREIGN KEY (case_id) REFERENCES employee_relations.er_case(id) ON DELETE CASCADE
);

CREATE TABLE employee_relations.er_investigation_interview (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    investigation_id UUID NOT NULL,
    interviewee_name VARCHAR(255) NOT NULL,
    interviewee_role VARCHAR(100),
    interview_date DATE NOT NULL,
    summary TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL,

    CONSTRAINT fk_er_interview_investigation FOREIGN KEY (investigation_id) REFERENCES employee_relations.er_investigation(id) ON DELETE CASCADE
);

CREATE TABLE employee_relations.er_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    investigation_id UUID NOT NULL,
    description VARCHAR(500),
    attachment_id UUID, -- soft FK to core_hr.attachment

    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by VARCHAR(255) NOT NULL,

    CONSTRAINT fk_er_evidence_investigation FOREIGN KEY (investigation_id) REFERENCES employee_relations.er_investigation(id) ON DELETE CASCADE
);

CREATE INDEX idx_er_case_tenant ON employee_relations.er_case(tenant_id, status);
CREATE INDEX idx_er_case_employee ON employee_relations.er_case(employee_id);
CREATE INDEX idx_er_case_owner ON employee_relations.er_case(owner_username);
CREATE INDEX idx_er_case_no ON employee_relations.er_case(case_no);
CREATE INDEX idx_er_note_case ON employee_relations.er_case_note(case_id);
CREATE INDEX idx_er_investigation_case ON employee_relations.er_investigation(case_id);
CREATE INDEX idx_er_interview_investigation ON employee_relations.er_investigation_interview(investigation_id);
CREATE INDEX idx_er_evidence_investigation ON employee_relations.er_evidence(investigation_id);

COMMENT ON SCHEMA employee_relations IS 'M445 — CONFIDENTIAL employee relations cases, investigations, evidence';
COMMENT ON TABLE employee_relations.er_case IS 'ER cases: grievances, complaints, investigations, disciplinary links';
COMMENT ON COLUMN employee_relations.er_case.is_confidential IS 'Only HR_ADMIN + owner can see';
COMMENT ON COLUMN employee_relations.er_case.legal_hold IS 'Blocks close/delete operations';
COMMENT ON COLUMN employee_relations.er_case.employee_id IS 'Nullable for anonymous complaints';
