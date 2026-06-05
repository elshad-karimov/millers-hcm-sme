-- M119 — Custom report builder Phase 1.
--
-- A custom_report row is a saved spec a user (or HR) composed in the
-- builder UI. It references a curated CustomReportSource (an enum on
-- the Java side, NOT a foreign key — the source whitelist lives in
-- code so columns can't be invented from the API). fieldsJson stores
-- the selected field keys in display order; filtersJson the WHERE
-- clauses; sortJson the ORDER BY clauses. The runner validates every
-- key against the source's allow-list before assembling SQL.

CREATE TABLE reporting.custom_report (
    id           UUID PRIMARY KEY,
    name         VARCHAR(160) NOT NULL,
    description  TEXT,
    source_key   VARCHAR(64)  NOT NULL,   -- maps to CustomReportSource enum
    fields_json  JSONB        NOT NULL,   -- array of {key:string}
    filters_json JSONB        NOT NULL,   -- array of {key, op, value, value2?}
    sort_json    JSONB        NOT NULL,   -- array of {key, direction}
    row_limit    INTEGER      NOT NULL DEFAULT 1000,
    shared       BOOLEAN      NOT NULL DEFAULT FALSE,
    owner_user   VARCHAR(160) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_run_at  TIMESTAMPTZ,
    last_run_rows INTEGER
);

-- One report name per owner — saving the same name re-shows as "update"
-- rather than silently spawning duplicates.
CREATE UNIQUE INDEX ux_custom_report_owner_name
    ON reporting.custom_report (owner_user, lower(name));

-- Listing pattern: "mine + shared" — a partial index keeps the shared
-- branch tight without forcing a full scan when most reports are private.
CREATE INDEX ix_custom_report_shared
    ON reporting.custom_report (updated_at DESC) WHERE shared = TRUE;
