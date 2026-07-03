-- HCM_11 Benefits Administration — M376 (Phase B.1)
-- Enrollment overhaul: coverage-tier selection, contribution snapshot, dependent-level
-- junction, and the fuller status lifecycle. Contributions are SNAPSHOT at enrolment so a
-- later plan/tier price change never silently alters an active enrollment's payroll deduction.

-- ── Fuller status lifecycle ──────────────────────────────────────────────────
ALTER TABLE compbenefits.benefit_enrollment DROP CONSTRAINT benefit_enrollment_status_check;
ALTER TABLE compbenefits.benefit_enrollment ADD CONSTRAINT benefit_enrollment_status_check
    CHECK (status IN ('DRAFT','PENDING_APPROVAL','ENROLLED','WAIVED',
                      'SUSPENDED','TERMINATED','CANCELLED','REJECTED'));

-- ── Coverage tier + plan year + contribution snapshot ────────────────────────
ALTER TABLE compbenefits.benefit_enrollment
    ADD COLUMN coverage_tier_code    varchar(30),
    ADD COLUMN plan_year             int,
    ADD COLUMN employer_contribution numeric(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN employee_contribution numeric(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN currency              varchar(10) NOT NULL DEFAULT 'AZN';

-- Backfill existing enrollments from their plan's flat contribution (M108 rows).
UPDATE compbenefits.benefit_enrollment e
SET employer_contribution = p.employer_contribution,
    employee_contribution = p.employee_contribution,
    currency              = p.currency,
    plan_year             = p.plan_year
FROM compbenefits.benefit_plan p
WHERE e.plan_id = p.id;

-- ── Dependent-level enrollment junction ──────────────────────────────────────
-- Replaces M108's bare dependents_covered int with links to real dependent records.
CREATE TABLE compbenefits.benefit_enrollment_dependent (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     varchar(64) NOT NULL DEFAULT 'default',
    enrollment_id uuid NOT NULL REFERENCES compbenefits.benefit_enrollment (id) ON DELETE CASCADE,
    dependent_id  uuid NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT benefit_enrollment_dependent_unique UNIQUE (enrollment_id, dependent_id)
);
CREATE INDEX benefit_enrollment_dependent_enr_idx
    ON compbenefits.benefit_enrollment_dependent (enrollment_id);

COMMENT ON TABLE compbenefits.benefit_enrollment_dependent IS
    'HCM_11 M376 — which dependents a benefit enrollment covers (replaces the bare count).';
