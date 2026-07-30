-- ----------------------------------------------------------------------------
-- V311 — Make reference-table UNIQUE constraints per-tenant (multi-tenancy Phase 5).
--
-- Several reference/config tables declared a natural-key UNIQUE that predates
-- multi-tenancy and is tenant-blind (e.g. workflow_definition (code, version)).
-- With two tenants, cloning the same reference codes into a second tenant would
-- violate these — so the tenant now becomes part of the key: a code is unique
-- WITHIN a tenant, not globally.
--
-- Only the tables whose UNIQUE did not already include tenant_id are touched;
-- the ones already keyed (salary_component, loan_type, asset_category, ppe_item,
-- goal_type, rating_scale) are left alone. Child tables (workflow_step,
-- rating_scale_value) stay keyed on their parent FK, which is itself per-tenant.
--
-- Existing single-tenant data is unaffected (all rows are tenant_id='default').
-- ----------------------------------------------------------------------------

-- workflow definitions: (code, version) -> (tenant_id, code, version)
ALTER TABLE workflow.workflow_definition DROP CONSTRAINT uq_workflow_definition_code_version;
ALTER TABLE workflow.workflow_definition ADD CONSTRAINT uq_workflow_definition_code_version
    UNIQUE (tenant_id, code, version);

-- letter templates: (code, language) -> (tenant_id, code, language)
ALTER TABLE hr_letters.letter_template DROP CONSTRAINT uq_letter_template_code_lang;
ALTER TABLE hr_letters.letter_template ADD CONSTRAINT uq_letter_template_code_lang
    UNIQUE (tenant_id, code, language);

-- leave taxonomy: code -> (tenant_id, code)
ALTER TABLE leave_mgmt.leave_category DROP CONSTRAINT leave_category_code_key;
ALTER TABLE leave_mgmt.leave_category ADD CONSTRAINT leave_category_code_key
    UNIQUE (tenant_id, code);
ALTER TABLE leave_mgmt.leave_group DROP CONSTRAINT leave_group_code_key;
ALTER TABLE leave_mgmt.leave_group ADD CONSTRAINT leave_group_code_key
    UNIQUE (tenant_id, code);
ALTER TABLE leave_mgmt.leave_type DROP CONSTRAINT leave_type_code_key;
ALTER TABLE leave_mgmt.leave_type ADD CONSTRAINT leave_type_code_key
    UNIQUE (tenant_id, code);

-- payroll groups: code -> (tenant_id, code)
ALTER TABLE payroll.payroll_group DROP CONSTRAINT payroll_group_code_key;
ALTER TABLE payroll.payroll_group ADD CONSTRAINT payroll_group_code_key
    UNIQUE (tenant_id, code);

-- statutory rules: (code, jurisdiction, effective_from) -> +tenant_id
ALTER TABLE payroll.statutory_rule DROP CONSTRAINT statutory_rule_code_jurisdiction_effective_from_key;
ALTER TABLE payroll.statutory_rule ADD CONSTRAINT statutory_rule_code_jurisdiction_effective_from_key
    UNIQUE (tenant_id, code, jurisdiction, effective_from);

-- permission types: code -> (tenant_id, code)
ALTER TABLE permission.permission_type DROP CONSTRAINT permission_type_code_key;
ALTER TABLE permission.permission_type ADD CONSTRAINT permission_type_code_key
    UNIQUE (tenant_id, code);

-- recruitment document types: code -> (tenant_id, code)
ALTER TABLE recruitment.document_type DROP CONSTRAINT document_type_code_key;
ALTER TABLE recruitment.document_type ADD CONSTRAINT document_type_code_key
    UNIQUE (tenant_id, code);

-- staffing reason master: (category, code) -> (tenant_id, category, code)
ALTER TABLE staffing.reason_master DROP CONSTRAINT uq_reason_master_cat_code;
ALTER TABLE staffing.reason_master ADD CONSTRAINT uq_reason_master_cat_code
    UNIQUE (tenant_id, category, code);

-- holidays: (jurisdiction, holiday_date) -> (tenant_id, jurisdiction, holiday_date)
ALTER TABLE core_hr.holiday DROP CONSTRAINT holiday_jurisdiction_holiday_date_key;
ALTER TABLE core_hr.holiday ADD CONSTRAINT holiday_jurisdiction_holiday_date_key
    UNIQUE (tenant_id, jurisdiction, holiday_date);

-- notice-period rules: (employment_type, on_probation) -> +tenant_id
ALTER TABLE lifecycle.notice_period_rule DROP CONSTRAINT uq_notice_rule;
ALTER TABLE lifecycle.notice_period_rule ADD CONSTRAINT uq_notice_rule
    UNIQUE (tenant_id, employment_type, on_probation);
