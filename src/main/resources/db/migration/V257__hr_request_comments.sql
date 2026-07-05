-- V257: M437 HR service request comments

CREATE TABLE selfservice.hr_service_request_comment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    request_id UUID NOT NULL,
    author_username VARCHAR(255) NOT NULL,
    is_internal BOOLEAN NOT NULL DEFAULT FALSE,
    body VARCHAR(4000) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_comment_request FOREIGN KEY (request_id) REFERENCES selfservice.hr_service_request(id) ON DELETE CASCADE
);

CREATE INDEX idx_hr_comment_request ON selfservice.hr_service_request_comment(tenant_id, request_id);
CREATE INDEX idx_hr_comment_created ON selfservice.hr_service_request_comment(created_at DESC);

COMMENT ON TABLE selfservice.hr_service_request_comment IS 'M437 — Comments/notes on HR service requests';
COMMENT ON COLUMN selfservice.hr_service_request_comment.is_internal IS 'Internal notes visible to HR only; employees see only non-internal comments on their own requests';
