-- ----------------------------------------------------------------------------
-- V313 — Make user-entered `code` UNIQUE constraints per-tenant (multi-tenancy).
--
-- These tables carry an operator-chosen natural code (shift "MORNING", grade
-- "G5", course "SEC-101", …) with a tenant-blind UNIQUE(code). Two tenants must
-- be free to use the same codes, so the tenant becomes part of the key.
--
-- Scope: only single-column UNIQUE(code) that were tenant-blind. NOT touched:
--   * `*_no` / token / hash keys — minted by global sequences or random, so
--     globally unique and collision-free across tenants (interleaved numbering
--     is a cosmetic nicety, not a correctness bug — per-tenant numbering
--     sequences remain a documented follow-up);
--   * (parent_uuid, …) composites — naturally per-tenant (the UUID parent FK
--     belongs to exactly one tenant);
--   * org_unit (version_id, code) — version_id is a tenant-scoped FK.
--
-- No FK references any of these code uniques (verified), so drop+recreate is safe.
-- Existing single-tenant data is unaffected (all rows tenant_id='default').
-- ----------------------------------------------------------------------------

ALTER TABLE attendance.shift            DROP CONSTRAINT shift_code_key;
ALTER TABLE attendance.shift            ADD CONSTRAINT shift_code_key UNIQUE (tenant_id, code);
ALTER TABLE attendance.shift_pattern    DROP CONSTRAINT shift_pattern_code_key;
ALTER TABLE attendance.shift_pattern    ADD CONSTRAINT shift_pattern_code_key UNIQUE (tenant_id, code);
ALTER TABLE attendance.work_schedule    DROP CONSTRAINT work_schedule_code_key;
ALTER TABLE attendance.work_schedule    ADD CONSTRAINT work_schedule_code_key UNIQUE (tenant_id, code);

ALTER TABLE comp_benefits.allowance_type     DROP CONSTRAINT allowance_type_code_key;
ALTER TABLE comp_benefits.allowance_type     ADD CONSTRAINT allowance_type_code_key UNIQUE (tenant_id, code);
ALTER TABLE comp_benefits.bonus_matrix_rule  DROP CONSTRAINT bonus_matrix_rule_code_key;
ALTER TABLE comp_benefits.bonus_matrix_rule  ADD CONSTRAINT bonus_matrix_rule_code_key UNIQUE (tenant_id, code);

ALTER TABLE compbenefits.benefit_plan   DROP CONSTRAINT benefit_plan_code_key;
ALTER TABLE compbenefits.benefit_plan   ADD CONSTRAINT benefit_plan_code_key UNIQUE (tenant_id, code);
ALTER TABLE compbenefits.comp_cycle     DROP CONSTRAINT comp_cycle_code_key;
ALTER TABLE compbenefits.comp_cycle     ADD CONSTRAINT comp_cycle_code_key UNIQUE (tenant_id, code);

ALTER TABLE engagement.survey_template  DROP CONSTRAINT survey_template_code_key;
ALTER TABLE engagement.survey_template  ADD CONSTRAINT survey_template_code_key UNIQUE (tenant_id, code);

ALTER TABLE learning.competency         DROP CONSTRAINT competency_code_key;
ALTER TABLE learning.competency         ADD CONSTRAINT competency_code_key UNIQUE (tenant_id, code);
ALTER TABLE learning.course             DROP CONSTRAINT course_code_key;
ALTER TABLE learning.course             ADD CONSTRAINT course_code_key UNIQUE (tenant_id, code);

ALTER TABLE lifecycle.checklist_template DROP CONSTRAINT checklist_template_code_key;
ALTER TABLE lifecycle.checklist_template ADD CONSTRAINT checklist_template_code_key UNIQUE (tenant_id, code);

ALTER TABLE organization.legal_entity   DROP CONSTRAINT legal_entity_code_key;
ALTER TABLE organization.legal_entity   ADD CONSTRAINT legal_entity_code_key UNIQUE (tenant_id, code);
ALTER TABLE organization.location       DROP CONSTRAINT location_code_key;
ALTER TABLE organization.location       ADD CONSTRAINT location_code_key UNIQUE (tenant_id, code);

ALTER TABLE performance.review_cycle    DROP CONSTRAINT review_cycle_code_key;
ALTER TABLE performance.review_cycle    ADD CONSTRAINT review_cycle_code_key UNIQUE (tenant_id, code);

ALTER TABLE recruitment.interview_kit   DROP CONSTRAINT interview_kit_code_key;
ALTER TABLE recruitment.interview_kit   ADD CONSTRAINT interview_kit_code_key UNIQUE (tenant_id, code);

ALTER TABLE staffing.grade              DROP CONSTRAINT grade_code_key;
ALTER TABLE staffing.grade              ADD CONSTRAINT grade_code_key UNIQUE (tenant_id, code);
ALTER TABLE staffing.job_family         DROP CONSTRAINT job_family_code_key;
ALTER TABLE staffing.job_family         ADD CONSTRAINT job_family_code_key UNIQUE (tenant_id, code);
ALTER TABLE staffing.job_function       DROP CONSTRAINT job_function_code_key;
ALTER TABLE staffing.job_function       ADD CONSTRAINT job_function_code_key UNIQUE (tenant_id, code);
ALTER TABLE staffing.position           DROP CONSTRAINT position_code_key;
ALTER TABLE staffing.position           ADD CONSTRAINT position_code_key UNIQUE (tenant_id, code);
