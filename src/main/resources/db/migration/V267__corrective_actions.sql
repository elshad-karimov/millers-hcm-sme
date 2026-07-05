-- V267: M447 corrective action plans

CREATE TABLE employee_relations.corrective_action_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    er_case_id UUID, -- nullable link to ER case
    disciplinary_action_id UUID, -- nullable link to M67 disciplinary action
    employee_id UUID NOT NULL,

    action_required VARCHAR(2000) NOT NULL,
    responsible_username VARCHAR(255) NOT NULL,
    due_date DATE NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'OPEN', -- OPEN, IN_PROGRESS, COMPLETED, OVERDUE, ESCALATED
    completed_at TIMESTAMPTZ,
    follow_up_date DATE,
    notes TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(255),

    CONSTRAINT fk_corrective_er_case FOREIGN KEY (er_case_id) REFERENCES employee_relations.er_case(id)
);

CREATE INDEX idx_corrective_tenant_employee ON employee_relations.corrective_action_plan(tenant_id, employee_id);
CREATE INDEX idx_corrective_status ON employee_relations.corrective_action_plan(status);
CREATE INDEX idx_corrective_due ON employee_relations.corrective_action_plan(due_date);
CREATE INDEX idx_corrective_responsible ON employee_relations.corrective_action_plan(responsible_username);

COMMENT ON TABLE employee_relations.corrective_action_plan IS 'M447 — Corrective action plans linked to ER cases or disciplinary actions';
COMMENT ON COLUMN employee_relations.corrective_action_plan.status IS 'Daily sweep marks past-due OPEN/IN_PROGRESS → OVERDUE';
