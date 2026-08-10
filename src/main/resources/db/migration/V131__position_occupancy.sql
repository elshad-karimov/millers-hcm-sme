-- ----------------------------------------------------------------------------
-- M246 — Phase D: Position occupancy + replacement workflow.
--
-- Two new tables behind every position:
--
--   staffing.position_occupancy   — one row per (position × employee ×
--                                   time window). Captures *who* sits
--                                   in *which* seat *when*, with
--                                   type=PRIMARY for normal assignments
--                                   and type=ACTING / TEMPORARY /
--                                   SECONDMENT for non-permanent fills.
--                                   FTE allocation supports position
--                                   sharing (two part-timers sharing a
--                                   1.0 FTE seat).
--
--   staffing.position_replacement — when someone leaves a position,
--                                   what happens to the seat:
--                                   open recruitment, freeze, close,
--                                   internal transfer, or assign an
--                                   acting replacement. Approval
--                                   workflow + audit breadcrumbs.
--
-- Backfill: every employee currently linked via employee.position_id
-- gets a synthetic PRIMARY occupancy row starting at their hire date
-- so the history isn't empty on day one. Pre-M246 transitions are
-- not reconstructible — the audit log has the raw events but not the
-- position-side delta.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS staffing.position_occupancy (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    position_id   UUID NOT NULL REFERENCES staffing.position(id) ON DELETE CASCADE,
    employee_id   UUID NOT NULL REFERENCES core_hr.employee(id),
    -- Type of relationship between employee and seat.
    --   PRIMARY     — regular long-term occupant (most rows)
    --   SECONDARY   — a person holding a non-primary seat in addition to
    --                 their main role (matrix / dotted-line)
    --   ACTING      — temporarily fulfilling a higher role while still
    --                 holding their home position
    --   TEMPORARY   — short-term cover (e.g. maternity replacement)
    --   SECONDMENT  — sent to another org for a defined period
    --   INTERN      — non-permanent internship
    --   CONTRACTOR  — non-employee contractor occupying a seat
    occupancy_type      VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',
    -- Decimal share of the seat's FTE capacity this row consumes
    -- (e.g. 0.50 for half-time). Two part-timers sharing one seat
    -- have two rows summing to 1.00 against the position's FTE.
    fte_allocation      NUMERIC(4,2) NOT NULL DEFAULT 1.00
                        CHECK (fte_allocation > 0 AND fte_allocation <= 1.00),
    start_date          DATE NOT NULL,
    -- Open-ended end_date means "currently active". When the occupancy
    -- ends, this is set to the last day inclusive.
    end_date            DATE,
    -- Why this occupancy ended.
    end_reason          VARCHAR(64),  -- RESIGNATION/TERMINATION/RETIREMENT/TRANSFER/PROMOTION/SECONDED_OUT/INTERIM_END/...
    end_notes           TEXT,
    -- For ACTING/TEMPORARY — the home position the person belongs to,
    -- so the SPA can show "Acting in Finance Director (home: Senior
    -- Accountant)".
    home_position_id    UUID REFERENCES staffing.position(id),
    -- Monthly allowance paid for acting in a higher role.
    acting_allowance    NUMERIC(14,2),
    acting_allowance_currency VARCHAR(3),
    notes               TEXT,
    -- Audit
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_occupancy_position
    ON staffing.position_occupancy(position_id, start_date DESC);
CREATE INDEX IF NOT EXISTS idx_occupancy_employee
    ON staffing.position_occupancy(employee_id, start_date DESC);
-- Partial index for the very common "is this seat currently filled?" query.
CREATE INDEX IF NOT EXISTS idx_occupancy_active
    ON staffing.position_occupancy(position_id) WHERE end_date IS NULL;

-- ── Replacement workflow ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS staffing.position_replacement (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    position_id             UUID NOT NULL REFERENCES staffing.position(id),
    leaving_employee_id     UUID NOT NULL REFERENCES core_hr.employee(id),
    -- Optional link to the specific occupancy that's ending.
    leaving_occupancy_id    UUID REFERENCES staffing.position_occupancy(id),
    -- Why they're leaving.
    reason                  VARCHAR(64) NOT NULL,
    last_working_day        DATE NOT NULL,
    -- What HR / manager wants to do with the seat once it's empty.
    action                  VARCHAR(32) NOT NULL,
        -- OPEN_RECRUITMENT (default) — post a vacancy
        -- INTERNAL_TRANSFER         — already-identified replacement
        -- ACTING                    — assign someone to act temporarily
        -- FREEZE                    — freeze the position
        -- CLOSE                     — close the position permanently
    -- If filling: identified replacement + their dates.
    replacement_employee_id UUID REFERENCES core_hr.employee(id),
    replacement_start_date  DATE,
    handover_overlap_days   INT DEFAULT 0,
    -- Linked vacancy when action = OPEN_RECRUITMENT.
    vacancy_id              UUID REFERENCES recruitment.vacancy(id),
    -- Workflow status
    status                  VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    -- Approval breadcrumbs
    submitted_by            VARCHAR(120),
    submitted_at            TIMESTAMPTZ,
    approved_by             VARCHAR(120),
    approved_at             TIMESTAMPTZ,
    rejected_by             VARCHAR(120),
    rejected_at             TIMESTAMPTZ,
    reject_reason           TEXT,
    completed_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    cancel_reason           TEXT,
    notes                   TEXT,
    -- Audit
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              VARCHAR(120),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by              VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_replacement_position
    ON staffing.position_replacement(position_id);
CREATE INDEX IF NOT EXISTS idx_replacement_employee
    ON staffing.position_replacement(leaving_employee_id);
CREATE INDEX IF NOT EXISTS idx_replacement_status
    ON staffing.position_replacement(status);

-- ── Backfill ─────────────────────────────────────────────────────────
-- Synthesize a PRIMARY occupancy row for every currently-linked
-- employee so the position detail page is non-empty on first load.
-- Start date defaults to the employee's hire_date when available,
-- falling back to the position's effective_from, then today.
INSERT INTO staffing.position_occupancy
    (position_id, employee_id, occupancy_type, fte_allocation,
     start_date, end_date, created_by, notes)
SELECT
    e.position_id,
    e.id,
    'PRIMARY',
    1.00,
    COALESCE(e.hire_date, p.effective_from, CURRENT_DATE),
    NULL,
    'system',
    'Backfill from V131 — pre-M246 occupancy history was not preserved'
FROM core_hr.employee e
JOIN staffing.position p ON p.id = e.position_id
WHERE e.position_id IS NOT NULL
  AND e.employment_status IN ('ACTIVE', 'ON_PROBATION', 'ON_LEAVE')
  AND NOT EXISTS (
      SELECT 1 FROM staffing.position_occupancy o
      WHERE o.position_id = e.position_id
        AND o.employee_id = e.id
        AND o.end_date IS NULL
  );
