-- M448: EHS incidents + involved employees + witnesses
-- Phase E: Environmental Health & Safety

CREATE SCHEMA IF NOT EXISTS ehs;

CREATE SEQUENCE ehs.incident_no_seq START WITH 1;

CREATE TABLE ehs.incident (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(100) NOT NULL DEFAULT 'default',
    incident_no             VARCHAR(50) NOT NULL,
    incident_date           DATE NOT NULL,
    incident_time           TIME,
    work_location_id        UUID,
    org_unit_id             UUID,
    incident_type           VARCHAR(40) NOT NULL, -- INJURY/NEAR_MISS/UNSAFE_CONDITION/PROPERTY_DAMAGE/VEHICLE/CHEMICAL/FIRE/EQUIPMENT/VIOLENCE/ENVIRONMENTAL
    severity                VARCHAR(20) NOT NULL, -- MINOR/MODERATE/SERIOUS/CRITICAL
    reported_by_employee_id UUID NOT NULL,
    description             VARCHAR(4000) NOT NULL,
    immediate_action        VARCHAR(2000),
    investigation_required  BOOLEAN NOT NULL DEFAULT false,
    status                  VARCHAR(30) NOT NULL DEFAULT 'OPEN', -- OPEN/UNDER_INVESTIGATION/CLOSED
    closed_at               TIMESTAMP WITH TIME ZONE,
    created_by              VARCHAR(255) NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by              VARCHAR(255) NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT fk_incident_work_location FOREIGN KEY (work_location_id)
        REFERENCES core_hr.work_location(id),
    CONSTRAINT fk_incident_org_unit FOREIGN KEY (org_unit_id)
        REFERENCES core_hr.org_unit(id),
    CONSTRAINT fk_incident_reported_by FOREIGN KEY (reported_by_employee_id)
        REFERENCES core_hr.employee(id)
);

CREATE UNIQUE INDEX idx_incident_no_per_tenant ON ehs.incident(tenant_id, incident_no);
CREATE INDEX idx_incident_tenant_status ON ehs.incident(tenant_id, status);
CREATE INDEX idx_incident_tenant_date ON ehs.incident(tenant_id, incident_date DESC);
CREATE INDEX idx_incident_reported_by ON ehs.incident(reported_by_employee_id);

-- Involved employees (many-to-many)
CREATE TABLE ehs.incident_involved (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id UUID NOT NULL,
    employee_id UUID NOT NULL,

    CONSTRAINT fk_incident_involved_incident FOREIGN KEY (incident_id)
        REFERENCES ehs.incident(id) ON DELETE CASCADE,
    CONSTRAINT fk_incident_involved_employee FOREIGN KEY (employee_id)
        REFERENCES core_hr.employee(id),
    CONSTRAINT uq_incident_involved UNIQUE (incident_id, employee_id)
);

CREATE INDEX idx_incident_involved_incident ON ehs.incident_involved(incident_id);
CREATE INDEX idx_incident_involved_employee ON ehs.incident_involved(employee_id);

-- Witnesses
CREATE TABLE ehs.incident_witness (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id UUID NOT NULL,
    name        VARCHAR(200) NOT NULL,
    contact     VARCHAR(200),
    statement   VARCHAR(2000),

    CONSTRAINT fk_incident_witness_incident FOREIGN KEY (incident_id)
        REFERENCES ehs.incident(id) ON DELETE CASCADE
);

CREATE INDEX idx_incident_witness_incident ON ehs.incident_witness(incident_id);
