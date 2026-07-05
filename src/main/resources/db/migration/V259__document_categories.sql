-- V259: M439 document categories + versioning

CREATE TABLE core_hr.document_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    retention_days INT,
    auto_create_renewal_request BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL,

    UNIQUE(tenant_id, code)
);

CREATE INDEX idx_doc_category_tenant ON core_hr.document_category(tenant_id, active);

-- Extend employee_document with category + versioning
ALTER TABLE core_hr.employee_document
ADD COLUMN category_id UUID,
ADD COLUMN version INT NOT NULL DEFAULT 1,
ADD COLUMN previous_version_id UUID,
ADD CONSTRAINT fk_document_category FOREIGN KEY (category_id) REFERENCES core_hr.document_category(id),
ADD CONSTRAINT fk_document_previous_version FOREIGN KEY (previous_version_id) REFERENCES core_hr.employee_document(id);

CREATE INDEX idx_employee_document_category ON core_hr.employee_document(category_id);
CREATE INDEX idx_employee_document_version_chain ON core_hr.employee_document(previous_version_id);

COMMENT ON TABLE core_hr.document_category IS 'M439 — Document categories for employee documents (contracts, IDs, medical, etc)';
COMMENT ON COLUMN core_hr.document_category.mandatory IS 'Required for employee profile completeness';
COMMENT ON COLUMN core_hr.document_category.retention_days IS 'Legal retention period in days; NULL = indefinite';
COMMENT ON COLUMN core_hr.document_category.auto_create_renewal_request IS 'M441 — auto-create renewal service request on expiry';
COMMENT ON COLUMN core_hr.employee_document.version IS 'Version number; uploadNewVersion creates new row with version+1';
COMMENT ON COLUMN core_hr.employee_document.previous_version_id IS 'Chain to previous version; latest = current (max version per document chain)';

-- Seed common document categories
INSERT INTO core_hr.document_category (tenant_id, code, name, mandatory, retention_days, auto_create_renewal_request, sort_order, created_by) VALUES
('default', 'CONTRACT', 'Employment Contract', true, 27375, false, 1, 'system'), -- 75 years
('default', 'ID_CARD', 'National ID Card', true, NULL, true, 2, 'system'),
('default', 'PASSPORT', 'Passport', false, NULL, true, 3, 'system'),
('default', 'DIPLOMA', 'Educational Diploma', true, NULL, false, 4, 'system'),
('default', 'MEDICAL', 'Medical Certificate', false, 3650, true, 5, 'system'), -- 10 years
('default', 'POLICE_CLEARANCE', 'Police Clearance', false, 2555, false, 6, 'system'), -- 7 years
('default', 'BANK_DETAILS', 'Bank Account Details', true, NULL, false, 7, 'system'),
('default', 'TAX_CERT', 'Tax Certificate', false, 2555, true, 8, 'system'), -- 7 years
('default', 'WORK_PERMIT', 'Work Permit', false, NULL, true, 9, 'system'),
('default', 'OTHER', 'Other Documents', false, 1825, false, 99, 'system'); -- 5 years
