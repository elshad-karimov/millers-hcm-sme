-- M450: EHS risk assessments + safety inspections

CREATE TABLE ehs.risk_assessment (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(100) NOT NULL DEFAULT 'default',
    work_location_id        UUID,
    org_unit_id             UUID,
    job_task                VARCHAR(200) NOT NULL,
    hazard                  VARCHAR(2000) NOT NULL,
    likelihood              INTEGER NOT NULL CHECK (likelihood BETWEEN 1 AND 5),
    impact                  INTEGER NOT NULL CHECK (impact BETWEEN 1 AND 5),
    risk_score              INTEGER NOT NULL,
    control_measures        VARCHAR(2000),
    responsible_username    VARCHAR(255),
    review_date             DATE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT/APPROVED/ARCHIVED
    approved_by             VARCHAR(255),
    approved_at             TIMESTAMP WITH TIME ZONE,
    created_by              VARCHAR(255) NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by              VARCHAR(255) NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT fk_risk_work_location FOREIGN KEY (work_location_id)
        REFERENCES organization.location(id),
    CONSTRAINT fk_risk_org_unit FOREIGN KEY (org_unit_id)
        REFERENCES organization.org_unit(id)
);

CREATE INDEX idx_risk_tenant ON ehs.risk_assessment(tenant_id);
CREATE INDEX idx_risk_status ON ehs.risk_assessment(tenant_id, status);
CREATE INDEX idx_risk_score ON ehs.risk_assessment(tenant_id, risk_score DESC);

CREATE TABLE ehs.safety_inspection (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(100) NOT NULL DEFAULT 'default',
    work_location_id        UUID,
    inspection_date         DATE NOT NULL,
    inspector_username      VARCHAR(255) NOT NULL,
    title                   VARCHAR(200) NOT NULL,
    overall_score           INTEGER,
    status                  VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED/COMPLETED
    notes                   VARCHAR(2000),
    created_by              VARCHAR(255) NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by              VARCHAR(255) NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT fk_inspection_work_location FOREIGN KEY (work_location_id)
        REFERENCES organization.location(id)
);

CREATE INDEX idx_inspection_tenant ON ehs.safety_inspection(tenant_id);
CREATE INDEX idx_inspection_date ON ehs.safety_inspection(tenant_id, inspection_date DESC);
CREATE INDEX idx_inspection_status ON ehs.safety_inspection(tenant_id, status);

CREATE TABLE ehs.inspection_finding (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_id           UUID NOT NULL,
    item_label              VARCHAR(300) NOT NULL,
    finding_status          VARCHAR(20) NOT NULL, -- OK/NON_COMPLIANT
    notes                   VARCHAR(1000),
    corrective_action_id    UUID,

    CONSTRAINT fk_finding_inspection FOREIGN KEY (inspection_id)
        REFERENCES ehs.safety_inspection(id) ON DELETE CASCADE
);

CREATE INDEX idx_finding_inspection ON ehs.inspection_finding(inspection_id);
CREATE INDEX idx_finding_corrective_action ON ehs.inspection_finding(corrective_action_id);
