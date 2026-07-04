-- M78 / Phase 2 — Multi-status overlays + new statuses + rehire flag
-- (P2-13, P2-14, P2-15).
--
-- Two changes:
--   1. employee gains rehire_eligible / previous_employee_id / rehire_reason
--      so a terminated employee can be brought back without losing the
--      audit chain.
--   2. new employee_status_overlay table — concurrent statuses can co-exist
--      on top of the scalar employment_status (e.g. an ACTIVE employee can
--      simultaneously hold an ON_LEAVE overlay coming from an approved
--      leave request and a BUSINESS_TRIP overlay coming from a trip).
--
-- The scalar employee.employment_status stays the canonical "primary"
-- status. Overlays are additive metadata — no existing flow is altered.
-- Listeners that previously set employment_status='ON_LEAVE' keep working;
-- new code paths can opt into overlays without breaking anything.

-- ── employee — rehire flag + previous link ────────────────────────────────

ALTER TABLE core_hr.employee
    ADD COLUMN IF NOT EXISTS rehire_eligible      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS previous_employee_id UUID,
    ADD COLUMN IF NOT EXISTS rehire_reason        TEXT;

-- Soft self-FK — keep the chain even if the predecessor row is hard-deleted.
ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS fk_employee_previous;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT fk_employee_previous
    FOREIGN KEY (previous_employee_id)
    REFERENCES core_hr.employee (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_employee_previous
    ON core_hr.employee (previous_employee_id)
    WHERE previous_employee_id IS NOT NULL;


-- ── employee_status_overlay ───────────────────────────────────────────────
-- One row per concurrent overlay. The {source, source_id} pair is informational
-- — it lets the UI link back to whatever produced this overlay (a leave
-- request, a business trip, a manual entry by HR).
--
-- At most one open (effective_to IS NULL) overlay per (employee, status):
-- you can't be "on maternity leave" twice at once. Multiple distinct status
-- overlays can co-exist (LEAVE + EDUCATIONAL_LEAVE in theory, though rare).

CREATE TABLE IF NOT EXISTS core_hr.employee_status_overlay (
    id                  UUID         PRIMARY KEY,
    employee_id         UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    status              VARCHAR(32)  NOT NULL,
    source              VARCHAR(32)  NOT NULL DEFAULT 'MANUAL',
    source_id           UUID,
    effective_from      DATE         NOT NULL,
    effective_to        DATE,
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(80),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          VARCHAR(80),
    CONSTRAINT chk_status_overlay_status CHECK (status IN (
        'ON_LEAVE','ON_BUSINESS_TRIP','MATERNITY_LEAVE','MILITARY_SERVICE',
        'EDUCATIONAL_LEAVE','GARDEN_LEAVE','SUSPENDED','NON_ACTIVE'
    )),
    CONSTRAINT chk_status_overlay_source CHECK (source IN (
        'MANUAL','LEAVE_REQUEST','BUSINESS_TRIP','PERMISSION','DISCIPLINARY','LIFECYCLE'
    )),
    CONSTRAINT chk_status_overlay_dates CHECK (
        effective_to IS NULL OR effective_to >= effective_from
    )
);

CREATE INDEX IF NOT EXISTS idx_status_overlay_employee
    ON core_hr.employee_status_overlay (employee_id, effective_from DESC);

CREATE INDEX IF NOT EXISTS idx_status_overlay_status
    ON core_hr.employee_status_overlay (status);

-- At most one OPEN overlay per (employee, status). Closed overlays can
-- repeat freely.
CREATE UNIQUE INDEX IF NOT EXISTS uq_status_overlay_one_open
    ON core_hr.employee_status_overlay (employee_id, status)
    WHERE effective_to IS NULL;
