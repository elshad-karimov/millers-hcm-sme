-- M55: BI Export / Power BI audit log (PRD §17.6)
CREATE TABLE reporting.bi_export_log (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    entity       VARCHAR(50)  NOT NULL,
    format       VARCHAR(10)  NOT NULL,   -- ODATA | CSV
    requested_by VARCHAR(255) NOT NULL,
    row_count    INTEGER      NOT NULL DEFAULT 0,
    exported_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ON reporting.bi_export_log (exported_at DESC);
CREATE INDEX ON reporting.bi_export_log (entity, format);
