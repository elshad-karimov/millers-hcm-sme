-- M144 — OrgUnit lifecycle states + closure (§26)
-- Replaces the binary active/inactive flag with a four-state machine:
-- PLANNED → ACTIVE ↔ CLOSING → CLOSED (plus CLOSED → ACTIVE reopen).
--
-- The existing active boolean is kept for backward-compat; the service
-- derives it from lifecycle_state (false only for CLOSED).

ALTER TABLE organization.org_unit
    ADD COLUMN IF NOT EXISTS lifecycle_state    varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS planned_open_date  date,
    ADD COLUMN IF NOT EXISTS closure_announced_date date,
    ADD COLUMN IF NOT EXISTS closure_reason     text,
    ADD COLUMN IF NOT EXISTS closed_date        date,
    ADD COLUMN IF NOT EXISTS closed_by          varchar(80);

-- Back-fill: units currently active=false → CLOSED, active=true → ACTIVE.
UPDATE organization.org_unit
SET lifecycle_state = CASE WHEN active = false THEN 'CLOSED' ELSE 'ACTIVE' END;

-- Add LIFECYCLE_CHANGE to the check-constrained change_kind column if one exists.
-- (OrgUnitHistory uses Hibernate EnumType.STRING so no DB check constraint.)
