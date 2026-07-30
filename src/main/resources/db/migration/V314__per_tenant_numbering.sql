-- ----------------------------------------------------------------------------
-- V314 — Per-tenant business numbering (multi-tenancy).
--
-- Business numbers (EMP-…, PR-…, LR-…, run_no, claim_no, …) were minted from
-- ~61 GLOBAL Postgres sequences, so a 2nd tenant's numbers continued the global
-- counter (interleaved) rather than starting fresh. This makes numbering
-- per-tenant: each tenant gets its own counter starting at 1.
--
--   * config.tenant_sequence  — one counter row per (tenant, sequence).
--   * config.next_tenant_seq(seq) — atomic UPSERT-and-return; reads the tenant
--     from the `hcm.tenant` GUC (set per-connection by TenantAwareDataSource),
--     defaulting to 'default'. Native repo/JDBC `nextval('x.y_seq')` calls are
--     rewritten to `config.next_tenant_seq('x.y_seq')`.
--
-- Because numbering is now per-tenant, the *_no UNIQUE constraints must also be
-- per-tenant (two tenants can each mint EMP-00001) — widened below.
--
-- Continuity: the 'default' tenant's counters are seeded from each global
-- sequence's current value, so its numbering carries on seamlessly.
-- ----------------------------------------------------------------------------

CREATE TABLE config.tenant_sequence (
    tenant_id VARCHAR(64)  NOT NULL,
    seq_name  VARCHAR(160) NOT NULL,
    next_val  BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, seq_name)
);

COMMENT ON TABLE config.tenant_sequence IS
  'Per-tenant business-number counters. next_tenant_seq() increments + returns.';

CREATE OR REPLACE FUNCTION config.next_tenant_seq(p_seq TEXT) RETURNS BIGINT AS $$
DECLARE
    v_tenant TEXT := COALESCE(NULLIF(current_setting('hcm.tenant', true), ''), 'default');
    v_next   BIGINT;
BEGIN
    INSERT INTO config.tenant_sequence (tenant_id, seq_name, next_val)
    VALUES (v_tenant, p_seq, 1)
    ON CONFLICT (tenant_id, seq_name)
    DO UPDATE SET next_val = config.tenant_sequence.next_val + 1
    RETURNING next_val INTO v_next;
    RETURN v_next;
END;
$$ LANGUAGE plpgsql;

-- Seed the incumbent 'default' tenant from every existing global sequence so its
-- numbering continues without a gap or collision.
DO $$
DECLARE
    r      RECORD;
    v_last BIGINT;
    v_called BOOLEAN;
BEGIN
    FOR r IN
        SELECT sequence_schema AS s, sequence_name AS n
        FROM information_schema.sequences
        WHERE sequence_schema NOT IN ('pg_catalog', 'information_schema')
    LOOP
        EXECUTE format('SELECT last_value, is_called FROM %I.%I', r.s, r.n)
            INTO v_last, v_called;
        INSERT INTO config.tenant_sequence (tenant_id, seq_name, next_val)
        VALUES ('default', r.s || '.' || r.n, CASE WHEN v_called THEN v_last ELSE 0 END)
        ON CONFLICT (tenant_id, seq_name) DO UPDATE SET next_val = EXCLUDED.next_val;
    END LOOP;
END $$;

-- ── Widen the 54 sequence-fed *_no UNIQUE constraints to (tenant_id, *_no) ──
-- so per-tenant numbering can't collide across tenants. No FK references these
-- (verified). Generated from pg_constraint.
ALTER TABLE attendance.shift_swap_request DROP CONSTRAINT shift_swap_request_request_no_key;
ALTER TABLE attendance.shift_swap_request ADD CONSTRAINT shift_swap_request_request_no_key UNIQUE (tenant_id, request_no);
ALTER TABLE business_trip.business_trip_request DROP CONSTRAINT business_trip_request_trip_no_key;
ALTER TABLE business_trip.business_trip_request ADD CONSTRAINT business_trip_request_trip_no_key UNIQUE (tenant_id, trip_no);
ALTER TABLE business_trip.expense_claim DROP CONSTRAINT expense_claim_claim_no_key;
ALTER TABLE business_trip.expense_claim ADD CONSTRAINT expense_claim_claim_no_key UNIQUE (tenant_id, claim_no);
ALTER TABLE business_trip.mileage_claim DROP CONSTRAINT mileage_claim_claim_no_key;
ALTER TABLE business_trip.mileage_claim ADD CONSTRAINT mileage_claim_claim_no_key UNIQUE (tenant_id, claim_no);
ALTER TABLE business_trip.reimbursement_batch DROP CONSTRAINT reimbursement_batch_batch_no_key;
ALTER TABLE business_trip.reimbursement_batch ADD CONSTRAINT reimbursement_batch_batch_no_key UNIQUE (tenant_id, batch_no);
ALTER TABLE comp_benefits.bonus_run DROP CONSTRAINT bonus_run_run_no_key;
ALTER TABLE comp_benefits.bonus_run ADD CONSTRAINT bonus_run_run_no_key UNIQUE (tenant_id, run_no);
ALTER TABLE comp_benefits.bonus_run_item DROP CONSTRAINT bonus_run_item_item_no_key;
ALTER TABLE comp_benefits.bonus_run_item ADD CONSTRAINT bonus_run_item_item_no_key UNIQUE (tenant_id, item_no);
ALTER TABLE comp_benefits.employee_allowance DROP CONSTRAINT employee_allowance_allowance_no_key;
ALTER TABLE comp_benefits.employee_allowance ADD CONSTRAINT employee_allowance_allowance_no_key UNIQUE (tenant_id, allowance_no);
ALTER TABLE core_hr.attachment DROP CONSTRAINT attachment_attachment_no_key;
ALTER TABLE core_hr.attachment ADD CONSTRAINT attachment_attachment_no_key UNIQUE (tenant_id, attachment_no);
ALTER TABLE core_hr.employee DROP CONSTRAINT employee_employee_no_key;
ALTER TABLE core_hr.employee ADD CONSTRAINT employee_employee_no_key UNIQUE (tenant_id, employee_no);
ALTER TABLE core_hr.personal_info_change_request DROP CONSTRAINT personal_info_change_request_request_no_key;
ALTER TABLE core_hr.personal_info_change_request ADD CONSTRAINT personal_info_change_request_request_no_key UNIQUE (tenant_id, request_no);
ALTER TABLE employee_relations.er_case DROP CONSTRAINT er_case_case_no_key;
ALTER TABLE employee_relations.er_case ADD CONSTRAINT er_case_case_no_key UNIQUE (tenant_id, case_no);
ALTER TABLE engagement.reward_redemption DROP CONSTRAINT reward_redemption_redemption_no_key;
ALTER TABLE engagement.reward_redemption ADD CONSTRAINT reward_redemption_redemption_no_key UNIQUE (tenant_id, redemption_no);
ALTER TABLE hr_letters.letter_request DROP CONSTRAINT letter_request_request_no_key;
ALTER TABLE hr_letters.letter_request ADD CONSTRAINT letter_request_request_no_key UNIQUE (tenant_id, request_no);
ALTER TABLE learning.certificate DROP CONSTRAINT certificate_certificate_no_key;
ALTER TABLE learning.certificate ADD CONSTRAINT certificate_certificate_no_key UNIQUE (tenant_id, certificate_no);
ALTER TABLE learning.course DROP CONSTRAINT course_course_no_key;
ALTER TABLE learning.course ADD CONSTRAINT course_course_no_key UNIQUE (tenant_id, course_no);
ALTER TABLE learning.enrollment DROP CONSTRAINT enrollment_enrollment_no_key;
ALTER TABLE learning.enrollment ADD CONSTRAINT enrollment_enrollment_no_key UNIQUE (tenant_id, enrollment_no);
ALTER TABLE learning.learning_path DROP CONSTRAINT learning_path_path_no_key;
ALTER TABLE learning.learning_path ADD CONSTRAINT learning_path_path_no_key UNIQUE (tenant_id, path_no);
ALTER TABLE learning.training_plan DROP CONSTRAINT training_plan_plan_no_key;
ALTER TABLE learning.training_plan ADD CONSTRAINT training_plan_plan_no_key UNIQUE (tenant_id, plan_no);
ALTER TABLE leave_mgmt.leave_request DROP CONSTRAINT leave_request_request_no_key;
ALTER TABLE leave_mgmt.leave_request ADD CONSTRAINT leave_request_request_no_key UNIQUE (tenant_id, request_no);
ALTER TABLE lifecycle.asset_damage_loss_case DROP CONSTRAINT asset_damage_loss_case_case_no_key;
ALTER TABLE lifecycle.asset_damage_loss_case ADD CONSTRAINT asset_damage_loss_case_case_no_key UNIQUE (tenant_id, case_no);
ALTER TABLE lifecycle.asset_transfer DROP CONSTRAINT asset_transfer_transfer_no_key;
ALTER TABLE lifecycle.asset_transfer ADD CONSTRAINT asset_transfer_transfer_no_key UNIQUE (tenant_id, transfer_no);
ALTER TABLE lifecycle.contract_change DROP CONSTRAINT contract_change_change_no_key;
ALTER TABLE lifecycle.contract_change ADD CONSTRAINT contract_change_change_no_key UNIQUE (tenant_id, change_no);
ALTER TABLE lifecycle.disciplinary_action DROP CONSTRAINT disciplinary_action_action_no_key;
ALTER TABLE lifecycle.disciplinary_action ADD CONSTRAINT disciplinary_action_action_no_key UNIQUE (tenant_id, action_no);
ALTER TABLE lifecycle.employee_movement_request DROP CONSTRAINT employee_movement_request_request_no_key;
ALTER TABLE lifecycle.employee_movement_request ADD CONSTRAINT employee_movement_request_request_no_key UNIQUE (tenant_id, request_no);
ALTER TABLE lifecycle.employment_contract DROP CONSTRAINT employment_contract_contract_no_key;
ALTER TABLE lifecycle.employment_contract ADD CONSTRAINT employment_contract_contract_no_key UNIQUE (tenant_id, contract_no);
ALTER TABLE lifecycle.offboarding_case DROP CONSTRAINT offboarding_case_case_no_key;
ALTER TABLE lifecycle.offboarding_case ADD CONSTRAINT offboarding_case_case_no_key UNIQUE (tenant_id, case_no);
ALTER TABLE lifecycle.offboarding_settlement DROP CONSTRAINT offboarding_settlement_settlement_no_key;
ALTER TABLE lifecycle.offboarding_settlement ADD CONSTRAINT offboarding_settlement_settlement_no_key UNIQUE (tenant_id, settlement_no);
ALTER TABLE lifecycle.onboarding_resource_request DROP CONSTRAINT onboarding_resource_request_request_no_key;
ALTER TABLE lifecycle.onboarding_resource_request ADD CONSTRAINT onboarding_resource_request_request_no_key UNIQUE (tenant_id, request_no);
ALTER TABLE lifecycle.resignation_request DROP CONSTRAINT resignation_request_resignation_no_key;
ALTER TABLE lifecycle.resignation_request ADD CONSTRAINT resignation_request_resignation_no_key UNIQUE (tenant_id, resignation_no);
ALTER TABLE lifecycle.termination_request DROP CONSTRAINT termination_request_termination_no_key;
ALTER TABLE lifecycle.termination_request ADD CONSTRAINT termination_request_termination_no_key UNIQUE (tenant_id, termination_no);
ALTER TABLE mobility.international_assignment DROP CONSTRAINT international_assignment_assignment_no_key;
ALTER TABLE mobility.international_assignment ADD CONSTRAINT international_assignment_assignment_no_key UNIQUE (tenant_id, assignment_no);
ALTER TABLE payroll.erp_export DROP CONSTRAINT erp_export_export_no_key;
ALTER TABLE payroll.erp_export ADD CONSTRAINT erp_export_export_no_key UNIQUE (tenant_id, export_no);
ALTER TABLE payroll.loan_request DROP CONSTRAINT loan_request_request_no_key;
ALTER TABLE payroll.loan_request ADD CONSTRAINT loan_request_request_no_key UNIQUE (tenant_id, request_no);
ALTER TABLE payroll.payroll_run DROP CONSTRAINT payroll_run_run_no_key;
ALTER TABLE payroll.payroll_run ADD CONSTRAINT payroll_run_run_no_key UNIQUE (tenant_id, run_no);
ALTER TABLE performance.goal DROP CONSTRAINT goal_goal_no_key;
ALTER TABLE performance.goal ADD CONSTRAINT goal_goal_no_key UNIQUE (tenant_id, goal_no);
ALTER TABLE performance.performance_review DROP CONSTRAINT performance_review_review_no_key;
ALTER TABLE performance.performance_review ADD CONSTRAINT performance_review_review_no_key UNIQUE (tenant_id, review_no);
ALTER TABLE permission.permission_request DROP CONSTRAINT permission_request_request_no_key;
ALTER TABLE permission.permission_request ADD CONSTRAINT permission_request_request_no_key UNIQUE (tenant_id, request_no);
ALTER TABLE recruitment.agency DROP CONSTRAINT agency_agency_no_key;
ALTER TABLE recruitment.agency ADD CONSTRAINT agency_agency_no_key UNIQUE (tenant_id, agency_no);
ALTER TABLE recruitment.agency_invoice DROP CONSTRAINT agency_invoice_invoice_no_key;
ALTER TABLE recruitment.agency_invoice ADD CONSTRAINT agency_invoice_invoice_no_key UNIQUE (tenant_id, invoice_no);
ALTER TABLE recruitment.agency_submission DROP CONSTRAINT agency_submission_submission_no_key;
ALTER TABLE recruitment.agency_submission ADD CONSTRAINT agency_submission_submission_no_key UNIQUE (tenant_id, submission_no);
ALTER TABLE recruitment.application DROP CONSTRAINT application_application_no_key;
ALTER TABLE recruitment.application ADD CONSTRAINT application_application_no_key UNIQUE (tenant_id, application_no);
ALTER TABLE recruitment.assessment DROP CONSTRAINT assessment_assessment_no_key;
ALTER TABLE recruitment.assessment ADD CONSTRAINT assessment_assessment_no_key UNIQUE (tenant_id, assessment_no);
ALTER TABLE recruitment.candidate DROP CONSTRAINT candidate_candidate_no_key;
ALTER TABLE recruitment.candidate ADD CONSTRAINT candidate_candidate_no_key UNIQUE (tenant_id, candidate_no);
ALTER TABLE recruitment.interview DROP CONSTRAINT interview_interview_no_key;
ALTER TABLE recruitment.interview ADD CONSTRAINT interview_interview_no_key UNIQUE (tenant_id, interview_no);
ALTER TABLE recruitment.job_posting DROP CONSTRAINT job_posting_posting_no_key;
ALTER TABLE recruitment.job_posting ADD CONSTRAINT job_posting_posting_no_key UNIQUE (tenant_id, posting_no);
ALTER TABLE recruitment.offer DROP CONSTRAINT offer_offer_no_key;
ALTER TABLE recruitment.offer ADD CONSTRAINT offer_offer_no_key UNIQUE (tenant_id, offer_no);
ALTER TABLE recruitment.pre_hire_check DROP CONSTRAINT pre_hire_check_check_no_key;
ALTER TABLE recruitment.pre_hire_check ADD CONSTRAINT pre_hire_check_check_no_key UNIQUE (tenant_id, check_no);
ALTER TABLE recruitment.referral DROP CONSTRAINT referral_referral_no_key;
ALTER TABLE recruitment.referral ADD CONSTRAINT referral_referral_no_key UNIQUE (tenant_id, referral_no);
ALTER TABLE recruitment.vacancy DROP CONSTRAINT vacancy_vacancy_no_key;
ALTER TABLE recruitment.vacancy ADD CONSTRAINT vacancy_vacancy_no_key UNIQUE (tenant_id, vacancy_no);
ALTER TABLE reporting.report_definition DROP CONSTRAINT report_definition_definition_no_key;
ALTER TABLE reporting.report_definition ADD CONSTRAINT report_definition_definition_no_key UNIQUE (tenant_id, definition_no);
ALTER TABLE reporting.report_run DROP CONSTRAINT report_run_run_no_key;
ALTER TABLE reporting.report_run ADD CONSTRAINT report_run_run_no_key UNIQUE (tenant_id, run_no);
ALTER TABLE reporting.report_schedule DROP CONSTRAINT report_schedule_schedule_no_key;
ALTER TABLE reporting.report_schedule ADD CONSTRAINT report_schedule_schedule_no_key UNIQUE (tenant_id, schedule_no);
ALTER TABLE selfservice.hr_service_request DROP CONSTRAINT hr_service_request_request_no_key;
ALTER TABLE selfservice.hr_service_request ADD CONSTRAINT hr_service_request_request_no_key UNIQUE (tenant_id, request_no);
