-- M451: EHS corrective actions + PPE management

CREATE TABLE ehs.corrective_action (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(100) NOT NULL DEFAULT 'default',
    incident_id             UUID,
    inspection_id           UUID,
    risk_assessment_id      UUID,
    description             VARCHAR(2000) NOT NULL,
    responsible_username    VARCHAR(255) NOT NULL,
    due_date                DATE NOT NULL,
    priority                VARCHAR(20) NOT NULL, -- LOW/MEDIUM/HIGH
    status                  VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN/IN_PROGRESS/COMPLETED/OVERDUE
    evidence_attachment_id  UUID,
    closed_at               TIMESTAMP WITH TIME ZONE,
    created_by              VARCHAR(255) NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by              VARCHAR(255) NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT fk_corrective_incident FOREIGN KEY (incident_id)
        REFERENCES ehs.incident(id),
    CONSTRAINT fk_corrective_inspection FOREIGN KEY (inspection_id)
        REFERENCES ehs.safety_inspection(id),
    CONSTRAINT fk_corrective_risk FOREIGN KEY (risk_assessment_id)
        REFERENCES ehs.risk_assessment(id)
);

CREATE INDEX idx_corrective_tenant ON ehs.corrective_action(tenant_id);
CREATE INDEX idx_corrective_status ON ehs.corrective_action(tenant_id, status);
CREATE INDEX idx_corrective_due_date ON ehs.corrective_action(tenant_id, due_date);
CREATE INDEX idx_corrective_responsible ON ehs.corrective_action(tenant_id, responsible_username);

CREATE TABLE ehs.ppe_item (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(100) NOT NULL DEFAULT 'default',
    code                    VARCHAR(50) NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    ppe_type                VARCHAR(40) NOT NULL, -- HELMET/GLOVES/SHOES/VEST/GOGGLES/MASK/EAR_PROTECTION/CLOTHING/OTHER
    default_expiry_months   INTEGER,
    active                  BOOLEAN NOT NULL DEFAULT true,
    created_by              VARCHAR(255) NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by              VARCHAR(255) NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT uq_ppe_code_per_tenant UNIQUE (tenant_id, code)
);

CREATE INDEX idx_ppe_tenant_active ON ehs.ppe_item(tenant_id, active);

-- Seed default PPE items
INSERT INTO ehs.ppe_item (code, name, ppe_type, default_expiry_months, active, created_by, updated_by)
VALUES
    ('HELMET', 'Safety Helmet', 'HELMET', 24, true, 'system', 'system'),
    ('GLOVES', 'Safety Gloves', 'GLOVES', 6, true, 'system', 'system'),
    ('SHOES', 'Safety Shoes', 'SHOES', 12, true, 'system', 'system'),
    ('VEST', 'Safety Vest', 'VEST', 24, true, 'system', 'system'),
    ('GOGGLES', 'Safety Goggles', 'GOGGLES', 24, true, 'system', 'system'),
    ('MASK', 'Respiratory Mask', 'MASK', 1, true, 'system', 'system'),
    ('EAR_PROT', 'Ear Protection', 'EAR_PROTECTION', 12, true, 'system', 'system');

CREATE TABLE ehs.ppe_assignment (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(100) NOT NULL DEFAULT 'default',
    employee_id             UUID NOT NULL,
    ppe_item_id             UUID NOT NULL,
    issued_at               DATE NOT NULL,
    expiry_date             DATE NOT NULL,
    returned_at             DATE,
    condition_at_issue      VARCHAR(200),
    condition_at_return     VARCHAR(200),
    notes                   VARCHAR(1000),
    created_by              VARCHAR(255) NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by              VARCHAR(255) NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT fk_ppe_assignment_employee FOREIGN KEY (employee_id)
        REFERENCES core_hr.employee(id),
    CONSTRAINT fk_ppe_assignment_item FOREIGN KEY (ppe_item_id)
        REFERENCES ehs.ppe_item(id)
);

CREATE INDEX idx_ppe_assignment_tenant ON ehs.ppe_assignment(tenant_id);
CREATE INDEX idx_ppe_assignment_employee ON ehs.ppe_assignment(tenant_id, employee_id);
CREATE INDEX idx_ppe_assignment_expiry ON ehs.ppe_assignment(tenant_id, expiry_date);
