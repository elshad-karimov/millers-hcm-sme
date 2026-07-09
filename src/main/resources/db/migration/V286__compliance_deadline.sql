-- ----------------------------------------------------------------------------
-- M470 — Compliance deadlines and reminders
--
-- Tracks recurring compliance deadlines (monthly/quarterly/annual).
-- Linked to statutory report templates (optional) or standalone.
-- Daily scheduler computes next_due and notifies HR admins 7 days in advance.
-- ----------------------------------------------------------------------------

CREATE TABLE compliance.compliance_deadline (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      VARCHAR(64) NOT NULL DEFAULT 'default',
  template_id    UUID,
  title          VARCHAR(255) NOT NULL,
  frequency      VARCHAR(20) NOT NULL,
  due_day        INT NOT NULL,
  month          INT,
  active         BOOLEAN NOT NULL DEFAULT true,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by     VARCHAR(200),

  CONSTRAINT fk_deadline_template FOREIGN KEY (template_id)
    REFERENCES compliance.statutory_report_template(id),
  CONSTRAINT deadline_freq_ck CHECK (frequency IN ('MONTHLY', 'QUARTERLY', 'ANNUAL')),
  CONSTRAINT deadline_due_day_ck CHECK (due_day BETWEEN 1 AND 31),
  CONSTRAINT deadline_month_ck CHECK (month IS NULL OR (month BETWEEN 1 AND 12))
);

CREATE INDEX idx_deadline_tenant ON compliance.compliance_deadline(tenant_id, active);
CREATE INDEX idx_deadline_template ON compliance.compliance_deadline(template_id);
