-- M169 (PRD §8.1.3): Employee Document Management
-- Dedicated table for categorised HR documents attached to an employee.
-- Each row carries a document type, optional expiry, access-restriction flag,
-- and a soft-FK to core_hr.attachment (the actual file stored in MinIO).

CREATE TABLE IF NOT EXISTS core_hr.employee_document (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    employee_id     UUID            NOT NULL
                        REFERENCES core_hr.employee(id) ON DELETE CASCADE,
    document_type   VARCHAR(40)     NOT NULL,
    attachment_id   UUID            REFERENCES core_hr.attachment(id),
    title           VARCHAR(255),
    expiry_date     DATE,
    restricted      BOOLEAN         NOT NULL DEFAULT FALSE,
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(80),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_employee_document_employee
    ON core_hr.employee_document(employee_id);

CREATE INDEX IF NOT EXISTS idx_employee_document_expiry
    ON core_hr.employee_document(expiry_date)
    WHERE expiry_date IS NOT NULL;

COMMENT ON TABLE  core_hr.employee_document IS 'Categorised HR document registry per employee (M169 / PRD §8.1.3).';
COMMENT ON COLUMN core_hr.employee_document.document_type IS 'EmployeeDocumentType enum value.';
COMMENT ON COLUMN core_hr.employee_document.attachment_id IS 'Soft-FK to core_hr.attachment; null until a file is uploaded.';
COMMENT ON COLUMN core_hr.employee_document.restricted    IS 'When TRUE only HR_ADMIN / SYSTEM_ADMIN may view the record.';
