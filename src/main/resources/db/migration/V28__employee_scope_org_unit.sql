-- Org-unit-based ABAC scoping for HR_SPECIALIST (PRD 14.9).
--
-- When set, the linked Keycloak user (via core_hr.employee.username) is
-- scoped to the descendants of this org_unit in the active org-structure
-- version, instead of the wide HR_SPECIALIST default. When NULL,
-- HR_SPECIALIST keeps its current behaviour (unrestricted) — back-compat
-- for the seeded `hrspec` user that's not yet linked to an employee.
--
-- The FK is to organization.org_unit(id) which is per-version. In
-- practice the caller's resolver picks the unit's id from whichever
-- version is currently ACTIVE; storing a per-version id is fine because
-- org versions move forward via a copy-on-write workflow (the previous
-- version is archived, not deleted).

ALTER TABLE core_hr.employee
    ADD COLUMN scope_org_unit_id UUID
        REFERENCES organization.org_unit(id);

CREATE INDEX idx_employee_scope_org_unit
    ON core_hr.employee (scope_org_unit_id)
    WHERE scope_org_unit_id IS NOT NULL;
