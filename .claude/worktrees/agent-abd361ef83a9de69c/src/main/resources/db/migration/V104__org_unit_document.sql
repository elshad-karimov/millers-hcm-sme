-- M147: Org-unit document registry + expiry tracking (§31).
--
-- Each org unit can have zero or more documents (licences, permits,
-- certificates, agreements, etc.) attached. Rows with a non-null expiry_date
-- participate in the shared ExpiryAlertScheduler — zero changes required
-- there; the new ExpiryAlertSource bean plugs in automatically.

CREATE TABLE organization.org_unit_document (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_unit_id     UUID        NOT NULL
                        REFERENCES organization.org_unit(id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    doc_type        VARCHAR(64),          -- LICENSE, PERMIT, CERTIFICATE, AGREEMENT, OTHER, …
    document_ref    VARCHAR(400),         -- external URL / file path / document number
    issued_date     DATE,
    expiry_date     DATE,
    responsible_employee_id UUID,         -- soft FK → core_hr.employee; expiry-alert recipient
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(80),
    updated_by      VARCHAR(80)
);

CREATE INDEX idx_org_unit_doc_unit    ON organization.org_unit_document (org_unit_id);
CREATE INDEX idx_org_unit_doc_expiry  ON organization.org_unit_document (expiry_date)
    WHERE expiry_date IS NOT NULL;
