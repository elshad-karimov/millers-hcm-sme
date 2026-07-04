-- M103 — Named successor nominations for positions.
--
-- Complements M92's bucket-based 9-box placement with explicit named records:
-- "Employee X is the designated successor for Position Y, at readiness tier T".
-- One position can have multiple successors (at different readiness tiers);
-- one employee can be a successor for multiple positions.

CREATE TABLE performance.succession_nomination (
    id                  uuid PRIMARY KEY,
    position_id         uuid NOT NULL,   -- ref staffing.position
    nominee_employee_id uuid NOT NULL,   -- the designated successor
    nominated_by        varchar(80),
    readiness_tier      varchar(20) NOT NULL,
    notes               text,
    cancelled_at        timestamptz,
    cancellation_reason text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT succession_nomination_readiness_check
        CHECK (readiness_tier IN ('READY_NOW','READY_SOON','READY_LONG_TERM','UNDER_DEVELOPMENT'))
);

-- One active nomination per (position, nominee) pair.
CREATE UNIQUE INDEX succession_nomination_active_unique
    ON performance.succession_nomination (position_id, nominee_employee_id)
    WHERE cancelled_at IS NULL;

CREATE INDEX succession_nomination_position_idx
    ON performance.succession_nomination (position_id)
    WHERE cancelled_at IS NULL;

CREATE INDEX succession_nomination_nominee_idx
    ON performance.succession_nomination (nominee_employee_id)
    WHERE cancelled_at IS NULL;

COMMENT ON TABLE performance.succession_nomination IS
    'M103 — Explicit named-successor record for a position + readiness tier.';
