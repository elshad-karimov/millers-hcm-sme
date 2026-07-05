-- V260: M440 signature requests (simplified — internal signing only for now)

CREATE TABLE core_hr.signature_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    title VARCHAR(500) NOT NULL,
    employee_document_id UUID,
    letter_request_id UUID,
    attachment_id UUID,

    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, CANCELLED
    provider VARCHAR(50) NOT NULL DEFAULT 'INTERNAL',

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL,

    CONSTRAINT fk_sig_employee_document FOREIGN KEY (employee_document_id) REFERENCES core_hr.employee_document(id),
    CONSTRAINT fk_sig_attachment FOREIGN KEY (attachment_id) REFERENCES core_hr.attachment(id)
);

CREATE TABLE core_hr.signature_request_signer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    request_id UUID NOT NULL,
    username VARCHAR(255) NOT NULL,
    employee_id UUID,

    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, SIGNED, DECLINED
    signed_at TIMESTAMPTZ,
    decline_reason VARCHAR(1000),

    CONSTRAINT fk_signer_request FOREIGN KEY (request_id) REFERENCES core_hr.signature_request(id) ON DELETE CASCADE,
    UNIQUE(request_id, username)
);

CREATE INDEX idx_signature_request_tenant ON core_hr.signature_request(tenant_id, status);
CREATE INDEX idx_signature_signer_request ON core_hr.signature_request_signer(request_id);
CREATE INDEX idx_signature_signer_username ON core_hr.signature_request_signer(tenant_id, username, status);

COMMENT ON TABLE core_hr.signature_request IS 'M440 — Electronic signature requests for documents/letters';
COMMENT ON COLUMN core_hr.signature_request.provider IS 'INTERNAL (click+timestamp) or DOCUSIGN (seam)';
