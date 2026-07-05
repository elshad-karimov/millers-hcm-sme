-- M449: EHS injury reports + return-to-work plans
-- Medical confidentiality: injury_report + RTW reads = HR_ADMIN only

CREATE TABLE ehs.injury_report (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(100) NOT NULL DEFAULT 'default',
    incident_id             UUID,
    employee_id             UUID NOT NULL,
    injury_type             VARCHAR(60),
    body_part               VARCHAR(60),
    severity                VARCHAR(20), -- MINOR/MODERATE/SERIOUS/CRITICAL (reuse IncidentSeverity)
    medical_treatment       BOOLEAN NOT NULL DEFAULT false,
    first_aid               BOOLEAN NOT NULL DEFAULT false,
    hospital                BOOLEAN NOT NULL DEFAULT false,
    lost_time_days          INTEGER NOT NULL DEFAULT 0,
    restricted_duty         BOOLEAN NOT NULL DEFAULT false,
    insurance_claim_ref     VARCHAR(100),
    notes                   VARCHAR(2000),
    created_by              VARCHAR(255) NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by              VARCHAR(255) NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT fk_injury_incident FOREIGN KEY (incident_id)
        REFERENCES ehs.incident(id),
    CONSTRAINT fk_injury_employee FOREIGN KEY (employee_id)
        REFERENCES core_hr.employee(id)
);

CREATE INDEX idx_injury_tenant ON ehs.injury_report(tenant_id);
CREATE INDEX idx_injury_incident ON ehs.injury_report(incident_id);
CREATE INDEX idx_injury_employee ON ehs.injury_report(employee_id);

CREATE TABLE ehs.return_to_work_plan (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(100) NOT NULL DEFAULT 'default',
    injury_report_id        UUID,
    employee_id             UUID NOT NULL,
    medical_clearance_date  DATE,
    restrictions            VARCHAR(2000),
    modified_schedule       VARCHAR(1000),
    manager_approved        BOOLEAN NOT NULL DEFAULT false,
    hr_approved             BOOLEAN NOT NULL DEFAULT false,
    status                  VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT/ACTIVE/COMPLETED
    closed_at               TIMESTAMP WITH TIME ZONE,
    created_by              VARCHAR(255) NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by              VARCHAR(255) NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT fk_rtw_injury FOREIGN KEY (injury_report_id)
        REFERENCES ehs.injury_report(id),
    CONSTRAINT fk_rtw_employee FOREIGN KEY (employee_id)
        REFERENCES core_hr.employee(id)
);

CREATE INDEX idx_rtw_tenant ON ehs.return_to_work_plan(tenant_id);
CREATE INDEX idx_rtw_injury ON ehs.return_to_work_plan(injury_report_id);
CREATE INDEX idx_rtw_employee ON ehs.return_to_work_plan(employee_id);
CREATE INDEX idx_rtw_status ON ehs.return_to_work_plan(tenant_id, status);
