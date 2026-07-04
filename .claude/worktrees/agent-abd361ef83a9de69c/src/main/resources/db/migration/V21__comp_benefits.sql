-- Compensation & Benefits module (PRD Section 8.15).
-- Bonus matrix that converts Performance calibrated recommendations into
-- bonus amounts and pushes them as payroll_bonus rows during a bonus run,
-- plus a catalogue of allowances + per-employee allowance assignments.

CREATE SEQUENCE comp_benefits.allowance_no_seq        START WITH 1;
CREATE SEQUENCE comp_benefits.bonus_run_no_seq        START WITH 1;
CREATE SEQUENCE comp_benefits.bonus_run_item_no_seq   START WITH 1;

-- Bonus matrix rules (PRD 8.15.2). Effective-dated lookup keyed by either:
--   1. A Performance recommendation (PROMOTION / BONUS_TIER_A / etc.) — strongest match wins.
--   2. A finalRating numeric band (min_rating .. max_rating inclusive) — fallback.
-- The rule can express either a bonus_percent (% of base salary) or a flat_amount.
CREATE TABLE comp_benefits.bonus_matrix_rule (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code              VARCHAR(64)  NOT NULL UNIQUE,
    description       TEXT,
    -- Match keys (any combination): a NULL match is treated as wildcard
    match_recommendation VARCHAR(40),     -- e.g. BONUS_TIER_A
    min_rating        NUMERIC(4,2),       -- inclusive
    max_rating        NUMERIC(4,2),       -- inclusive
    -- Amount: pick one
    bonus_percent     NUMERIC(5,2),       -- % of base salary
    flat_amount       NUMERIC(14,2),
    currency          VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    -- Optional cap
    max_amount        NUMERIC(14,2),
    -- Sort order: lower priority wins when multiple rules match (so admins can
    -- pin specific recommendations above the rating bands).
    priority          INTEGER      NOT NULL DEFAULT 100,
    effective_from    DATE         NOT NULL,
    effective_to      DATE,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(120),
    updated_by        VARCHAR(120),

    CONSTRAINT chk_bm_either CHECK (bonus_percent IS NOT NULL OR flat_amount IS NOT NULL),
    CONSTRAINT chk_bm_rating CHECK (
        (min_rating IS NULL AND max_rating IS NULL)
        OR (min_rating IS NOT NULL AND max_rating IS NOT NULL AND max_rating >= min_rating)
    ),
    CONSTRAINT chk_bm_dates  CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_bm_rule_active   ON comp_benefits.bonus_matrix_rule (active, priority);
CREATE INDEX idx_bm_rule_rec      ON comp_benefits.bonus_matrix_rule (match_recommendation);
CREATE INDEX idx_bm_rule_eff      ON comp_benefits.bonus_matrix_rule (effective_from, effective_to);

-- Catalogue of allowance types (PRD 8.15.4).
CREATE TABLE comp_benefits.allowance_type (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(40)  NOT NULL UNIQUE,
    name             VARCHAR(160) NOT NULL,
    description      TEXT,
    -- TRANSPORT, MEAL, MOBILE, HOUSING, FUEL, FAMILY, EDUCATION, MEDICAL, OTHER
    category         VARCHAR(32)  NOT NULL,
    taxable          BOOLEAN      NOT NULL DEFAULT TRUE,
    recurring        BOOLEAN      NOT NULL DEFAULT TRUE,        -- monthly recurring vs one-off
    default_amount   NUMERIC(14,2),
    currency         VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_alw_type_active ON comp_benefits.allowance_type (active);

-- Per-employee allowance assignments. Effective-dated; close prior open
-- record when a new one starts (handled in service).
CREATE TABLE comp_benefits.employee_allowance (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    allowance_no      VARCHAR(32)  NOT NULL UNIQUE,
    employee_id       UUID         NOT NULL,
    allowance_type_id UUID         NOT NULL REFERENCES comp_benefits.allowance_type (id),
    amount            NUMERIC(14,2) NOT NULL,
    currency          VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    effective_from    DATE         NOT NULL,
    effective_to      DATE,
    -- ACTIVE, ENDED, CANCELLED
    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    note              TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(120),
    updated_by        VARCHAR(120),

    CONSTRAINT chk_alw_amount CHECK (amount >= 0),
    CONSTRAINT chk_alw_dates  CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_alw_emp     ON comp_benefits.employee_allowance (employee_id, status);
CREATE INDEX idx_alw_type    ON comp_benefits.employee_allowance (allowance_type_id);
CREATE INDEX idx_alw_eff     ON comp_benefits.employee_allowance (effective_from, effective_to);

-- Bonus run: ties a Performance cycle to a Payroll period; one batch of
-- bonus derivations.
CREATE TABLE comp_benefits.bonus_run (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    run_no                   VARCHAR(32)  NOT NULL UNIQUE,
    name                     VARCHAR(200) NOT NULL,
    cycle_id                 UUID,                              -- soft ref into performance.review_cycle
    target_payroll_run_id    UUID,                              -- soft ref into payroll.payroll_run
    period_year              INTEGER      NOT NULL,
    period_month             INTEGER      NOT NULL,
    currency                 VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    -- DRAFT, GENERATED, PUSHED, CANCELLED
    status                   VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    total_amount             NUMERIC(14,2) NOT NULL DEFAULT 0,
    employee_count           INTEGER      NOT NULL DEFAULT 0,
    generated_at             TIMESTAMPTZ,
    generated_by             VARCHAR(120),
    pushed_at                TIMESTAMPTZ,
    pushed_by                VARCHAR(120),
    note                     TEXT,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               VARCHAR(120),

    CONSTRAINT chk_bonus_run_month CHECK (period_month BETWEEN 1 AND 12)
);

CREATE INDEX idx_bonus_run_cycle  ON comp_benefits.bonus_run (cycle_id);
CREATE INDEX idx_bonus_run_status ON comp_benefits.bonus_run (status);

CREATE TABLE comp_benefits.bonus_run_item (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    item_no             VARCHAR(32)  NOT NULL UNIQUE,
    run_id              UUID         NOT NULL REFERENCES comp_benefits.bonus_run (id) ON DELETE CASCADE,
    employee_id         UUID         NOT NULL,
    review_id           UUID,                                   -- soft ref into performance.performance_review
    recommendation      VARCHAR(40),
    final_rating        NUMERIC(4,2),
    base_salary         NUMERIC(14,2),
    bonus_percent       NUMERIC(5,2),
    bonus_amount        NUMERIC(14,2) NOT NULL,
    currency            VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    -- MATRIX_LOOKUP (matched against bonus_matrix_rule), REVIEW_OVERRIDE
    -- (review.bonusPercent set explicitly), MANUAL (admin override)
    source              VARCHAR(24)  NOT NULL,
    matrix_rule_id      UUID         REFERENCES comp_benefits.bonus_matrix_rule (id),
    -- Linked payroll.payroll_bonus row once pushed
    pushed_payroll_bonus_id UUID,
    note                TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (run_id, employee_id)
);

CREATE INDEX idx_bonus_item_run ON comp_benefits.bonus_run_item (run_id);

-- ---------- Seed data ----------

-- Default bonus matrix rules — keyed by Performance recommendations
-- (PRD 8.13 calibration phase produces these labels). Priority 10 = most
-- specific (recommendation match), 100 = generic rating-band fallback.
INSERT INTO comp_benefits.bonus_matrix_rule
  (code, description, match_recommendation, min_rating, max_rating,
   bonus_percent, currency, priority, effective_from, active, created_by)
VALUES
  ('PROMOTION',         'Promotion — top performer, may also receive a salary bump separately',
                                                'PROMOTION',         NULL, NULL,  20.00, 'AZN', 10, '2026-01-01', TRUE, 'system'),
  ('BONUS_TIER_A',      'Tier A — exceeds expectations',
                                                'BONUS_TIER_A',      NULL, NULL,  15.00, 'AZN', 10, '2026-01-01', TRUE, 'system'),
  ('BONUS_TIER_B',      'Tier B — meets expectations strongly',
                                                'BONUS_TIER_B',      NULL, NULL,  10.00, 'AZN', 10, '2026-01-01', TRUE, 'system'),
  ('BONUS_TIER_C',      'Tier C — meets expectations',
                                                'BONUS_TIER_C',      NULL, NULL,   5.00, 'AZN', 10, '2026-01-01', TRUE, 'system'),
  ('MERIT_INCREASE',    'Merit increase recommendation (no immediate bonus, handled via salary change)',
                                                'MERIT_INCREASE',    NULL, NULL,   0.00, 'AZN', 10, '2026-01-01', TRUE, 'system'),
  ('PIP',               'Performance Improvement Plan — no bonus',
                                                'PIP',               NULL, NULL,   0.00, 'AZN', 10, '2026-01-01', TRUE, 'system'),
  ('NONE',              'No recommendation — no bonus',
                                                'NONE',              NULL, NULL,   0.00, 'AZN', 10, '2026-01-01', TRUE, 'system'),
  ('RATING_BAND_4_PLUS','Fallback for finalRating ≥ 4 without an explicit recommendation',
                                                NULL,                4.00, 5.00,   8.00, 'AZN',100, '2026-01-01', TRUE, 'system'),
  ('RATING_BAND_3',     'Fallback for finalRating 3.00–3.99 without an explicit recommendation',
                                                NULL,                3.00, 3.99,   3.00, 'AZN',100, '2026-01-01', TRUE, 'system');

-- Default allowance catalogue (AZ market common allowances).
INSERT INTO comp_benefits.allowance_type
  (code, name, description, category, taxable, recurring, default_amount, currency, active)
VALUES
  ('TRANSPORT',  'Transport allowance',  'Daily commute reimbursement',     'TRANSPORT', TRUE,  TRUE,  100.00, 'AZN', TRUE),
  ('MEAL',       'Meal allowance',       'Daily meal stipend',              'MEAL',      TRUE,  TRUE,  150.00, 'AZN', TRUE),
  ('MOBILE',     'Mobile / data',        'Monthly mobile + data plan',      'MOBILE',    TRUE,  TRUE,   30.00, 'AZN', TRUE),
  ('HOUSING',    'Housing allowance',    'Monthly housing top-up',          'HOUSING',   TRUE,  TRUE,  300.00, 'AZN', TRUE),
  ('FUEL',       'Fuel allowance',       'Monthly fuel card top-up',        'FUEL',      TRUE,  TRUE,  100.00, 'AZN', TRUE),
  ('FAMILY',     'Family allowance',     'Per-dependant family allowance',  'FAMILY',    FALSE, TRUE,   50.00, 'AZN', TRUE);
