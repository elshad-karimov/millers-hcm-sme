-- M422: Hiring plan integration — link workforce plans to recruitment vacancies

CREATE TABLE staffing.hiring_plan_line (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    workforce_plan_id UUID NOT NULL,
    position_id UUID,
    target_start_date DATE,
    recruiter_employee_id UUID,
    recruitment_status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    vacancy_id UUID,
    headcount INT NOT NULL DEFAULT 1,
    notes TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_hiring_plan_workforce_plan
        FOREIGN KEY (workforce_plan_id) REFERENCES staffing.workforce_plan(id),
    CONSTRAINT fk_hiring_plan_position
        FOREIGN KEY (position_id) REFERENCES staffing.position(id),
    CONSTRAINT fk_hiring_plan_recruiter
        FOREIGN KEY (recruiter_employee_id) REFERENCES core_hr.employee(id)
);

CREATE INDEX idx_hiring_plan_workforce_plan ON staffing.hiring_plan_line(workforce_plan_id, tenant_id);
CREATE INDEX idx_hiring_plan_status ON staffing.hiring_plan_line(recruitment_status, tenant_id);
CREATE INDEX idx_hiring_plan_vacancy ON staffing.hiring_plan_line(vacancy_id);

COMMENT ON TABLE staffing.hiring_plan_line IS 'M422: Hiring plan generated from approved workforce plan NEW_HIRE lines';
COMMENT ON COLUMN staffing.hiring_plan_line.recruitment_status IS 'PLANNED/VACANCY_OPEN/HIRED/CANCELLED';
