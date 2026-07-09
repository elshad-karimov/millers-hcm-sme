-- ----------------------------------------------------------------------------
-- M469 — Statutory report submissions
--
-- Track submission lifecycle: DRAFT → GENERATED → SUBMITTED → ACCEPTED/REJECTED
-- Each submission ties to a template, covers a period, and stores the generated file.
-- ----------------------------------------------------------------------------

CREATE TABLE compliance.statutory_report_submission (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      VARCHAR(64) NOT NULL DEFAULT 'default',
  template_id    UUID NOT NULL,
  period_start   DATE NOT NULL,
  period_end     DATE NOT NULL,
  status         VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  attachment_id  UUID,
  generated_at   TIMESTAMPTZ,
  generated_by   VARCHAR(200),
  submitted_at   TIMESTAMPTZ,
  response_notes TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by     VARCHAR(200),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by     VARCHAR(200),

  CONSTRAINT fk_submission_template FOREIGN KEY (template_id)
    REFERENCES compliance.statutory_report_template(id),
  CONSTRAINT fk_submission_attachment FOREIGN KEY (attachment_id)
    REFERENCES core_hr.attachment(id),
  CONSTRAINT submission_status_ck CHECK (status IN ('DRAFT', 'GENERATED', 'SUBMITTED', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX idx_submission_tenant_template ON compliance.statutory_report_submission(tenant_id, template_id, period_start);
CREATE INDEX idx_submission_status ON compliance.statutory_report_submission(tenant_id, status);
