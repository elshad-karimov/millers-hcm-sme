-- M166 (PRD §9.3): Attach Document action on workflow instances.
-- Stores a reference to the attached document (MinIO object key or
-- signed URL) on the workflow_action row so the document is retrievable
-- from the audit history without a separate join table.
ALTER TABLE workflow.workflow_action
    ADD COLUMN IF NOT EXISTS document_ref VARCHAR(1024);

COMMENT ON COLUMN workflow.workflow_action.document_ref IS
    'For ATTACH_DOCUMENT actions: MinIO object key or external URL of
     the attached document.  NULL for all other action types.';
