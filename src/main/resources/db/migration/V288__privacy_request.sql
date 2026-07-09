-- ----------------------------------------------------------------------------
-- M472 — Privacy requests (GDPR compliance)
--
-- Tracks employee/subject data access, export, deletion, and correction requests.
-- Request types: ACCESS, EXPORT, DELETE, CORRECTION
-- Status: OPEN → IN_PROGRESS → COMPLETED / REJECTED
-- Due date: default +30 days from creation
-- ----------------------------------------------------------------------------

CREATE TABLE compliance.privacy_request (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        VARCHAR(64) NOT NULL DEFAULT 'default',
  employee_id      UUID,
  request_type     VARCHAR(20) NOT NULL,
  description      TEXT,
  status           VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  due_date         DATE NOT NULL,
  resolution_notes TEXT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by       VARCHAR(200),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by       VARCHAR(200),

  CONSTRAINT fk_privacy_request_employee FOREIGN KEY (employee_id)
    REFERENCES core_hr.employee(id),
  CONSTRAINT privacy_request_type_ck CHECK (request_type IN ('ACCESS', 'EXPORT', 'DELETE', 'CORRECTION')),
  CONSTRAINT privacy_request_status_ck CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'REJECTED'))
);

CREATE INDEX idx_privacy_request_tenant ON compliance.privacy_request(tenant_id, status);
CREATE INDEX idx_privacy_request_employee ON compliance.privacy_request(employee_id);
CREATE INDEX idx_privacy_request_due ON compliance.privacy_request(tenant_id, due_date) WHERE status IN ('OPEN', 'IN_PROGRESS');
