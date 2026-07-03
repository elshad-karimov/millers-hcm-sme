-- HCM_11 Benefits Administration — M377 (Phase B.2)
-- Enrollment approval workflow. A DRAFT enrollment can be submitted for HR approval;
-- on APPROVED it becomes ENROLLED (active), on REJECTED it is REJECTED. Reuses the
-- workflow engine (same pattern as SALARY_CHANGE_APPROVAL, V201).

INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    'BENEFIT_ENROLLMENT_APPROVAL',
    'Benefit Enrollment Approval',
    'HR approval for a benefit enrollment before coverage becomes active (HCM_11 §Enrollment).',
    FALSE,
    TRUE
);

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role, resolves_to_manager)
VALUES
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 1, 'HR benefits approval', 'ROLE_HR_ADMIN', FALSE);

-- Track the running approval workflow on the enrollment.
ALTER TABLE compbenefits.benefit_enrollment ADD COLUMN workflow_instance_id uuid;
