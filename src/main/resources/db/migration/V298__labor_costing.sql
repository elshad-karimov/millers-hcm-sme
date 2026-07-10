-- M485: Labor rates + cost allocation

-- Labor rates (hourly rate by grade or position)
CREATE TABLE payroll.labor_rate (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL,
    grade_id UUID, -- No FK: grade table may not exist
    position_id UUID, -- No FK: position table may not exist
    hourly_rate NUMERIC(14,2) NOT NULL CHECK (hourly_rate >= 0),
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(80),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(80),
    CHECK (
        (grade_id IS NOT NULL AND position_id IS NULL) OR
        (grade_id IS NULL AND position_id IS NOT NULL)
    ),
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_labor_rate_tenant ON payroll.labor_rate(tenant_id);
CREATE INDEX idx_labor_rate_grade ON payroll.labor_rate(grade_id, effective_from) WHERE grade_id IS NOT NULL;
CREATE INDEX idx_labor_rate_position ON payroll.labor_rate(position_id, effective_from) WHERE position_id IS NOT NULL;

-- Labor cost allocation (computed on timesheet approval)
CREATE TABLE payroll.labor_cost_allocation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL,
    timesheet_day_id UUID NOT NULL, -- No FK: timesheet_day is partitioned
    employee_id UUID NOT NULL REFERENCES core_hr.employee(id),
    work_date DATE NOT NULL,
    cost_center_id UUID, -- No FK: cost_center table may not exist
    project_id UUID REFERENCES timesheet.project(id),
    hours NUMERIC(6,2) NOT NULL CHECK (hours >= 0),
    hourly_rate NUMERIC(14,2) NOT NULL CHECK (hourly_rate >= 0),
    amount NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(80)
);

CREATE INDEX idx_labor_cost_tenant_date ON payroll.labor_cost_allocation(tenant_id, work_date);
CREATE INDEX idx_labor_cost_employee ON payroll.labor_cost_allocation(employee_id);
CREATE INDEX idx_labor_cost_project ON payroll.labor_cost_allocation(project_id) WHERE project_id IS NOT NULL;
CREATE INDEX idx_labor_cost_cost_center ON payroll.labor_cost_allocation(cost_center_id) WHERE cost_center_id IS NOT NULL;
CREATE INDEX idx_labor_cost_timesheet_day ON payroll.labor_cost_allocation(timesheet_day_id);

COMMENT ON TABLE payroll.labor_rate IS 'M485: Hourly labor rates by grade or position';
COMMENT ON TABLE payroll.labor_cost_allocation IS 'M485: Computed labor cost allocation per timesheet day';
