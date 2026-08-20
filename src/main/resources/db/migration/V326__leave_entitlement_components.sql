-- M151 — itemised annual leave entitlement.
--
-- Until now leave_balance.entitlement_days held one scalar per employee /
-- leave type / year. That is enough to say "32 days" and not enough to say
-- WHY it is 32 — which is exactly what an Azerbaijani labour inspection asks
-- for, because the total is a statutory sum:
--
--   base (Art. 114)                    + 30 or 21 days
--   seniority (Art. 116.1)             + 2 / 4 / 6 days
--   harmful conditions (Art. 115.2)    + 6 days
--   women with children (Art. 117)     + 2 or 5 days
--   blood donation                     + per donation
--
-- This migration adds the component ledger plus the driver fields each
-- component derives from. Rules were confirmed against the customer's live
-- personnel register (137 worked examples) — see the comments on each.
--
-- entitlement_days on leave_balance stays the authoritative total; the
-- components explain it and are recomputed into it. Nothing here changes a
-- balance on its own.

-- ── Component ledger ────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS leave_mgmt.leave_entitlement_component (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    employee_id     UUID         NOT NULL,
    leave_type_id   UUID         NOT NULL,
    year            INTEGER      NOT NULL,
    component_code  VARCHAR(32)  NOT NULL,
    days            NUMERIC(5,2) NOT NULL,
    -- DERIVED rows are owned by the resolvers and are overwritten on every
    -- recalculation. MANUAL rows are entered by HR and are never overwritten —
    -- that is what lets blood-donation days (which have no derivable driver)
    -- and one-off corrections survive a recalculation.
    source          VARCHAR(16)  NOT NULL,
    -- Human-readable justification for the row: "Specialist", "8.5 yrs
    -- professional experience → 5–10 bracket", "2 children under 14".
    -- Rendered on the entitlement breakdown and in audit exports, so an
    -- inspector can see the reasoning without reading code.
    basis           TEXT,
    computed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(80),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      VARCHAR(80),
    CONSTRAINT fk_lec_employee
        FOREIGN KEY (employee_id) REFERENCES core_hr.employee (id),
    CONSTRAINT fk_lec_leave_type
        FOREIGN KEY (leave_type_id) REFERENCES leave_mgmt.leave_type (id),
    CONSTRAINT chk_lec_component_code
        CHECK (component_code IN ('BASE', 'SENIORITY', 'HAZARDOUS',
                                  'CHILDREN', 'BLOOD_DONATION', 'OTHER')),
    CONSTRAINT chk_lec_source
        CHECK (source IN ('DERIVED', 'MANUAL')),
    -- Components add to the entitlement; a negative one would be a deduction
    -- masquerading as an entitlement and would not show up in the ledger as a
    -- deduction should. Corrections belong in adjustment_days.
    CONSTRAINT chk_lec_days_non_negative
        CHECK (days >= 0)
);

-- One row per component per employee/type/year: recalculation upserts on this
-- key, which is what makes re-running the resolvers idempotent.
CREATE UNIQUE INDEX IF NOT EXISTS uq_lec_employee_type_year_code
    ON leave_mgmt.leave_entitlement_component
       (tenant_id, employee_id, leave_type_id, year, component_code);

-- "Show me this employee's breakdown for 2026" — the read path behind the
-- entitlement tab.
CREATE INDEX IF NOT EXISTS idx_lec_employee_year
    ON leave_mgmt.leave_entitlement_component (tenant_id, employee_id, year);

-- "How many days of harmful-conditions leave are we carrying?" — liability
-- reporting slices by component across the whole workforce.
CREATE INDEX IF NOT EXISTS idx_lec_code_year
    ON leave_mgmt.leave_entitlement_component (tenant_id, component_code, year);

-- ── Driver: harmful/hazardous conditions (Art. 115.2) ───────────────────
--
-- Confirmed against the register: 18 employees receive 6 days. All are
-- offshore, but 21 other offshore staff receive nothing — the recipients are
-- Riggers, Welders, Mechanics and Electricians across both Labour and
-- Specialist classes. So the driver is the JOB, matching the statutory list
-- of harmful occupations, not the work location or the grade. Flagging the
-- position (rather than the employee) means the entitlement follows a
-- transfer in and out on its own.

ALTER TABLE staffing.position
    ADD COLUMN IF NOT EXISTS hazardous            BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS hazardous_leave_days NUMERIC(5,2);

-- A hazardous position without a day count would silently grant zero, which
-- reads identically to "not hazardous" on a payslip. Force them to agree.
ALTER TABLE staffing.position
    DROP CONSTRAINT IF EXISTS chk_position_hazardous_days;
ALTER TABLE staffing.position
    ADD CONSTRAINT chk_position_hazardous_days
    CHECK ((NOT hazardous AND hazardous_leave_days IS NULL)
           OR (hazardous AND hazardous_leave_days IS NOT NULL
               AND hazardous_leave_days > 0));

CREATE INDEX IF NOT EXISTS idx_position_hazardous
    ON staffing.position (tenant_id)
    WHERE hazardous;

-- ── Driver: women with children (Art. 117) ─────────────────────────────
--
-- The higher tier turns on a child with a disability under 16, which the
-- dependent record could not express. Nullable BOOLEAN rather than NOT NULL
-- DEFAULT FALSE: "not recorded" and "recorded as no" are different states
-- here, and quietly treating the first as the second would under-grant.

ALTER TABLE core_hr.employee_dependent
    ADD COLUMN IF NOT EXISTS has_disability BOOLEAN;

-- ── Driver + config: leave type ────────────────────────────────────────

ALTER TABLE leave_mgmt.leave_type
    -- Opt-in. Only types with this set are resolved from components; every
    -- other type keeps the existing accrual chain untouched, so this cannot
    -- disturb leave types that already work.
    ADD COLUMN IF NOT EXISTS entitlement_components_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    -- Base days keyed by employee.position_classification, e.g.
    -- {"Specialist": 30, "Labour": 21}. Confirmed against the register:
    -- 118/118 Specialists at 30, 15/16 Labour at 21. Held as config rather
    -- than code because the taxonomy is the tenant's own.
    ADD COLUMN IF NOT EXISTS base_days_by_classification_json JSONB,
    -- Seniority brackets on TOTAL PROFESSIONAL EXPERIENCE, e.g.
    -- [{"minYears":5,"days":2},{"minYears":10,"days":4},{"minYears":15,"days":6}].
    --
    -- Deliberately separate from the existing seniority_brackets_json, which
    -- brackets on COMPANY TENURE. The register settles which one is in force
    -- for annual leave: tenure-based brackets reproduced 19 of 136 rows,
    -- experience-based brackets reproduced 126 of 131. Keeping both columns
    -- means a tenant can run either without one silently overwriting the
    -- other.
    ADD COLUMN IF NOT EXISTS experience_brackets_json JSONB;

COMMENT ON COLUMN leave_mgmt.leave_type.experience_brackets_json IS
    'Seniority uplift bracketed on total professional experience (employee.professional_experience_years), NOT company tenure — see seniority_brackets_json for the tenure-based variant.';
