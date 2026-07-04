-- ----------------------------------------------------------------------------
-- M260 — PRD §40 Position Transfer Workflow.
--
-- Moves a position from one org unit (or cost centre / location) to
-- another with workflow approval. Different from M84 bulk reorg
-- (which moves many positions in one transaction): this is a single-
-- position transfer with explicit governance — request, approve,
-- complete-on-effective-date.
--
-- State machine:
--    DRAFT  →  PENDING_APPROVAL  →  APPROVED  →  COMPLETED
--                                  ↘
--                                   REJECTED
--    Any non-terminal  →  CANCELLED
--
-- On COMPLETED, the system writes the new org_unit_id / cost_centre /
-- location onto staffing.position. The lifecycle event is also
-- appended via the M243 position_lifecycle_event journal so the
-- audit trail for "where the position lived" is unbroken.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS staffing.position_transfer (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    position_id         UUID         NOT NULL,

    -- Snapshot of the FROM side at request time. Doesn't FK to org_unit
    -- because we want the historical record to survive org-unit rename
    -- or delete.
    from_org_unit_id    UUID,
    from_org_unit_label VARCHAR(200),
    from_cost_centre    VARCHAR(64),
    from_location       VARCHAR(160),

    -- TO side — what the position becomes after the transfer.
    to_org_unit_id      UUID,
    to_org_unit_label   VARCHAR(200),
    to_cost_centre      VARCHAR(64),
    to_location         VARCHAR(160),

    -- Reason — references the §22 reason master (VACANCY category is
    -- the closest semantic fit since transfers usually pair with
    -- restructure / expansion). Stored as the master's code, not
    -- the row id, so the label can change without breaking history.
    transfer_reason     VARCHAR(64),
    notes               TEXT,

    effective_date      DATE         NOT NULL,

    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',

    -- Audit breadcrumbs through the state machine.
    requested_by        VARCHAR(120),
    requested_at        TIMESTAMPTZ,
    submitted_by        VARCHAR(120),
    submitted_at        TIMESTAMPTZ,
    approved_by         VARCHAR(120),
    approved_at         TIMESTAMPTZ,
    rejected_by         VARCHAR(120),
    rejected_at         TIMESTAMPTZ,
    reject_reason       TEXT,
    completed_by        VARCHAR(120),
    completed_at        TIMESTAMPTZ,
    cancelled_by        VARCHAR(120),
    cancelled_at        TIMESTAMPTZ,
    cancel_reason       TEXT,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_position_transfer_status
        CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED',
                          'COMPLETED','REJECTED','CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_position_transfer_position
    ON staffing.position_transfer(position_id);

-- Partial index over the in-flight transfers — used by the SPA panel
-- to surface "there's already a pending transfer on this position".
CREATE INDEX IF NOT EXISTS idx_position_transfer_in_flight
    ON staffing.position_transfer(position_id, status)
    WHERE status IN ('DRAFT','PENDING_APPROVAL','APPROVED');
