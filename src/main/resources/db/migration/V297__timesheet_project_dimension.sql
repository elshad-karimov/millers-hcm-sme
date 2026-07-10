-- M484: Project dimension on timesheets

-- Project master (billing/costing dimension)
CREATE TABLE timesheet.project (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(80) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    billing_rate NUMERIC(14,2),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(80),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(80),
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_project_tenant_active ON timesheet.project(tenant_id, active);

-- Add project dimension to timesheet_day (partitioned table)
-- Note: Must add to all partitions individually
ALTER TABLE timesheet.timesheet_day ADD COLUMN project_id UUID;
ALTER TABLE timesheet.timesheet_day ADD COLUMN task_code VARCHAR(60);
ALTER TABLE timesheet.timesheet_day ADD COLUMN billable BOOLEAN DEFAULT false;

-- Add FK constraint to each partition (not the parent partitioned table)
ALTER TABLE timesheet.timesheet_day_2024 ADD CONSTRAINT fk_tsd_2024_project
    FOREIGN KEY (project_id) REFERENCES timesheet.project(id);

ALTER TABLE timesheet.timesheet_day_2025 ADD CONSTRAINT fk_tsd_2025_project
    FOREIGN KEY (project_id) REFERENCES timesheet.project(id);

ALTER TABLE timesheet.timesheet_day_2026 ADD CONSTRAINT fk_tsd_2026_project
    FOREIGN KEY (project_id) REFERENCES timesheet.project(id);

ALTER TABLE timesheet.timesheet_day_2027 ADD CONSTRAINT fk_tsd_2027_project
    FOREIGN KEY (project_id) REFERENCES timesheet.project(id);

ALTER TABLE timesheet.timesheet_day_default ADD CONSTRAINT fk_tsd_default_project
    FOREIGN KEY (project_id) REFERENCES timesheet.project(id);

-- Index on project_id for cost allocation queries
CREATE INDEX idx_tsd_2024_project ON timesheet.timesheet_day_2024(project_id) WHERE project_id IS NOT NULL;
CREATE INDEX idx_tsd_2025_project ON timesheet.timesheet_day_2025(project_id) WHERE project_id IS NOT NULL;
CREATE INDEX idx_tsd_2026_project ON timesheet.timesheet_day_2026(project_id) WHERE project_id IS NOT NULL;
CREATE INDEX idx_tsd_2027_project ON timesheet.timesheet_day_2027(project_id) WHERE project_id IS NOT NULL;
CREATE INDEX idx_tsd_default_project ON timesheet.timesheet_day_default(project_id) WHERE project_id IS NOT NULL;

COMMENT ON TABLE timesheet.project IS 'M484: Project master for timesheet billing/costing';
COMMENT ON COLUMN timesheet.timesheet_day.project_id IS 'M484: Project assignment for the day';
COMMENT ON COLUMN timesheet.timesheet_day.task_code IS 'M484: Task code within the project';
COMMENT ON COLUMN timesheet.timesheet_day.billable IS 'M484: Whether the time is billable';
