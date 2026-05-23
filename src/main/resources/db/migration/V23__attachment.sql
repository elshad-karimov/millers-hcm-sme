-- Attachment registry (PRD 16.4) — tracks files uploaded to MinIO and their
-- owning module + entity, so we can replace the textarea-style attachment_url
-- columns sprinkled across modules with first-class file uploads.
--
-- The blob lives in MinIO at (bucket, object_key); this table is the auditable
-- catalogue. Soft-delete via `deleted` keeps the audit trail intact when HR
-- removes a file (object stays in MinIO for retention).

CREATE SEQUENCE core_hr.attachment_no_seq START WITH 1;

CREATE TABLE core_hr.attachment (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    attachment_no       VARCHAR(32)  NOT NULL UNIQUE,
    bucket              VARCHAR(120) NOT NULL,
    object_key          VARCHAR(500) NOT NULL UNIQUE,
    original_filename   VARCHAR(255),
    content_type        VARCHAR(160),
    size_bytes          BIGINT,
    -- Soft pointer into the owning module's entity:
    owner_module        VARCHAR(40)  NOT NULL,    -- LEAVE, BUSINESS_TRIP, TERMINATION, ...
    owner_entity        VARCHAR(60)  NOT NULL,    -- LeaveRequest, BusinessTripRequest, ...
    owner_id            UUID         NOT NULL,
    uploaded_by         VARCHAR(120) NOT NULL,
    uploaded_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMPTZ,
    deleted_by          VARCHAR(120),

    CONSTRAINT chk_attachment_size CHECK (size_bytes IS NULL OR size_bytes >= 0)
);

CREATE INDEX idx_attachment_owner ON core_hr.attachment (owner_module, owner_entity, owner_id);
CREATE INDEX idx_attachment_uploaded_by ON core_hr.attachment (uploaded_by);
