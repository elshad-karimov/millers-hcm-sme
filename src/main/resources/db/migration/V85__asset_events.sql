-- M124 — Asset lifecycle event log.
--
-- M72 stored the current state of each employee_asset row but had no
-- per-asset timeline; the audit_log carried CREATE/UPDATE entries but
-- couldn't answer "show me everything that happened to this laptop in
-- the last year" without a fan-out query. This migration adds a
-- focused append-only event log.

CREATE TABLE core_hr.asset_event (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id        UUID         NOT NULL
                        REFERENCES core_hr.employee_asset (id) ON DELETE CASCADE,
    /* ASSIGN, RETURN, MARK_LOST, MARK_DAMAGED, WRITE_OFF,
     * REASSIGN, UPDATE_CONDITION */
    event_type      VARCHAR(32)  NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor           VARCHAR(160) NOT NULL,

    previous_status VARCHAR(20),
    new_status      VARCHAR(20),

    /* Employee context. assignedTo on the event captures who held the
     * asset at the point of the event so reissue can record both the
     * "returned by" and "assigned to" sides without a join. */
    previous_employee_id UUID,
    new_employee_id      UUID,

    condition       VARCHAR(40),
    notes           TEXT
);

CREATE INDEX ix_asset_event_asset  ON core_hr.asset_event (asset_id, occurred_at DESC);
CREATE INDEX ix_asset_event_type   ON core_hr.asset_event (event_type, occurred_at DESC);
