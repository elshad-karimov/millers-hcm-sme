-- ----------------------------------------------------------------------------
-- M298 — Onboarding PRD Phase A.1: rule-matched onboarding templates.
--
-- The M105 checklist engine selects templates only by flow type, so on hire
-- every active ONBOARDING template was attempted (and all but the first were
-- silently rejected by the one-active-per-(employee,flow) guard). This adds a
-- matching layer: each template carries an optional set of match rules and a
-- priority. On hire the ChecklistTemplateMatcher picks the single best-fitting
-- template by rule specificity → priority → code. A template with no rules is
-- a catch-all default (lowest specificity), preserving prior behaviour.
--
-- Match dimensions map to fields the Employee aggregate actually carries today
-- (department, position, org unit, work location, employment type, category,
-- nationality). Legal-entity / grade / contract-type / work-arrangement rules
-- are a deferred seam — they need Employee-side fields that don't exist yet.
-- ----------------------------------------------------------------------------

-- Tie-breaker + ordering weight on the template itself.
ALTER TABLE lifecycle.checklist_template
    ADD COLUMN IF NOT EXISTS priority int NOT NULL DEFAULT 0;

-- One match rule for a template. Every non-null column is an AND-ed filter;
-- a rule with all columns null matches everyone. A template matches an
-- employee if ANY of its active rules match.
CREATE TABLE IF NOT EXISTS lifecycle.checklist_template_rule (
    id                uuid PRIMARY KEY,
    template_id       uuid NOT NULL REFERENCES lifecycle.checklist_template(id) ON DELETE CASCADE,
    department_name   varchar(160),                 -- Employee.department_name (case-insensitive)
    position_id       uuid,                         -- Employee.position_id
    org_unit_id       uuid,                         -- Employee.org_unit_id
    work_location_id  uuid,                         -- Employee.work_location_id (branch/site)
    employment_type   varchar(20),                  -- Employee.employment_type enum name
    employee_category varchar(60),                  -- Employee.employee_category (case-insensitive)
    nationality       varchar(2),                   -- Employee.nationality (ISO alpha-2)
    active            boolean NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT checklist_template_rule_emp_type_check
        CHECK (employment_type IS NULL OR employment_type IN
            ('PERMANENT','FIXED_TERM','PART_TIME','PROBATIONARY','CONTRACTOR','INTERN'))
);

CREATE INDEX IF NOT EXISTS checklist_template_rule_template_idx
    ON lifecycle.checklist_template_rule (template_id);

COMMENT ON TABLE lifecycle.checklist_template_rule IS
    'M298 — onboarding/offboarding template match rule. Non-null columns are AND-ed filters; the most specific matching rule (most non-null columns) wins, tie-broken by template priority then code.';
