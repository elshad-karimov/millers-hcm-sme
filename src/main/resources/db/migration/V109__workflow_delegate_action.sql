-- M162 (PRD §9.3): Manual DELEGATE action on workflow instances.
-- An approver can explicitly hand off the current step to a specific user
-- (by username). The column stores the delegated-to username; when set,
-- inboxFor() returns the instance for that user regardless of role.
ALTER TABLE workflow.workflow_instance
    ADD COLUMN IF NOT EXISTS delegated_to VARCHAR(255);

COMMENT ON COLUMN workflow.workflow_instance.delegated_to IS
    'When a DELEGATE action is taken, stores the username of the person
     this step has been handed off to. NULL = no manual delegation active.';
