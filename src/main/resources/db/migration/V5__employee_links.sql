-- Link Employee to structured Org Unit and Position (PRD 8.1 + 8.3).
-- Soft references; the legacy department_name / position_title columns stay
-- for back-compat until every row is migrated.

ALTER TABLE core_hr.employee
    ADD COLUMN org_unit_id UUID,
    ADD COLUMN position_id UUID;

CREATE INDEX idx_employee_org_unit ON core_hr.employee (org_unit_id);
CREATE INDEX idx_employee_position ON core_hr.employee (position_id);
