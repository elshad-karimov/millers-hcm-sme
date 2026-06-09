-- M108 — Benefits administration.
--
-- Two concepts:
--   benefit_plan        — catalog of available plans (health, pension, etc.).
--   benefit_enrollment  — per-employee enrolment in a specific plan.

CREATE SCHEMA IF NOT EXISTS compbenefits;

CREATE TABLE compbenefits.benefit_plan (
    id              uuid PRIMARY KEY,
    code            varchar(40) NOT NULL UNIQUE,
    name            varchar(200) NOT NULL,
    description     text,
    benefit_type    varchar(20) NOT NULL,
    provider        varchar(160),
    coverage_details text,
    eligibility     text,
    employer_contribution numeric(14,2) NOT NULL DEFAULT 0,
    employee_contribution numeric(14,2) NOT NULL DEFAULT 0,
    currency        varchar(10) NOT NULL DEFAULT 'AZN',
    effective_from  date NOT NULL,
    effective_to    date,
    active          boolean NOT NULL DEFAULT true,
    created_by      varchar(80),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT benefit_plan_type_check
        CHECK (benefit_type IN ('HEALTH','DENTAL','VISION','LIFE_INSURANCE',
                                 'DISABILITY','PENSION','WELLNESS','OTHER')),
    CONSTRAINT benefit_plan_contributions_nonneg
        CHECK (employer_contribution >= 0 AND employee_contribution >= 0),
    CONSTRAINT benefit_plan_effective_window
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX benefit_plan_active_type_idx
    ON compbenefits.benefit_plan (active, benefit_type);

CREATE TABLE compbenefits.benefit_enrollment (
    id              uuid PRIMARY KEY,
    plan_id         uuid NOT NULL REFERENCES compbenefits.benefit_plan(id),
    employee_id     uuid NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'ENROLLED',
    start_date      date NOT NULL,
    end_date        date,
    dependents_covered int NOT NULL DEFAULT 0,
    notes           text,
    enrolled_by     varchar(80),
    enrolled_at     timestamptz NOT NULL DEFAULT now(),
    terminated_by   varchar(80),
    terminated_at   timestamptz,
    termination_reason text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT benefit_enrollment_status_check
        CHECK (status IN ('ENROLLED','WAIVED','TERMINATED')),
    CONSTRAINT benefit_enrollment_dependents_nonneg
        CHECK (dependents_covered >= 0),
    CONSTRAINT benefit_enrollment_dates_valid
        CHECK (end_date IS NULL OR end_date >= start_date)
);

-- At most one active enrollment per (employee, plan) pair.
CREATE UNIQUE INDEX benefit_enrollment_active_unique
    ON compbenefits.benefit_enrollment (employee_id, plan_id)
    WHERE status = 'ENROLLED';

CREATE INDEX benefit_enrollment_employee_idx
    ON compbenefits.benefit_enrollment (employee_id, status);

CREATE INDEX benefit_enrollment_plan_idx
    ON compbenefits.benefit_enrollment (plan_id, status);

COMMENT ON TABLE compbenefits.benefit_plan IS
    'M108 — Catalog of available benefit plans (health, pension, etc.).';
COMMENT ON TABLE compbenefits.benefit_enrollment IS
    'M108 — Per-employee enrolment in a benefit plan.';
