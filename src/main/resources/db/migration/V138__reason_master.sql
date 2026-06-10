-- ----------------------------------------------------------------------------
-- M259 — PRD §22: Reason Masters.
--
-- Lookup table backing the freeze / closure / replacement / vacancy
-- reason inputs across the staffing module. Previously these were free
-- text (M243 freeze_reason, closure_reason; M246 replacement reason),
-- which made cross-position analysis impossible — "how many positions
-- did we close because of a restructure last quarter?" required parsing
-- prose.
--
-- This table standardises the value list while still allowing the SPA
-- to accept ad-hoc text via the AutoComplete fallback when a master
-- doesn't cover the case yet.
--
-- Categories:
--   VACANCY      — why a position became vacant (transfer, retirement,
--                  resignation, expansion, …)
--   FREEZE       — why a position was frozen (M243 freeze breadcrumb)
--   CLOSURE      — why a position was closed (M243 closure breadcrumb)
--   REPLACEMENT  — why a replacement was triggered (M246 workflow)
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS staffing.reason_master (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    category     VARCHAR(32)  NOT NULL,
    code         VARCHAR(64)  NOT NULL,
    label        VARCHAR(200) NOT NULL,
    description  VARCHAR(500),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order   SMALLINT     NOT NULL DEFAULT 100,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_reason_master_cat_code UNIQUE (category, code)
);

CREATE INDEX IF NOT EXISTS idx_reason_master_cat_active
    ON staffing.reason_master(category, sort_order)
    WHERE active = TRUE;

-- ── Seed defaults — common labor-market reasons for an AZ deployment.
-- Sort order in increments of 10 so future inserts can slot between
-- existing rows without a renumber.

INSERT INTO staffing.reason_master (category, code, label, sort_order) VALUES
    -- VACANCY (why the seat opened)
    ('VACANCY', 'RESIGNATION',           'Resignation',                       10),
    ('VACANCY', 'TERMINATION',           'Termination',                       20),
    ('VACANCY', 'RETIREMENT',            'Retirement',                        30),
    ('VACANCY', 'INTERNAL_TRANSFER',     'Internal transfer',                 40),
    ('VACANCY', 'END_OF_CONTRACT',       'End of fixed-term contract',        50),
    ('VACANCY', 'EXPANSION',             'Headcount expansion',               60),
    ('VACANCY', 'RESTRUCTURE',           'Organizational restructure',        70),

    -- FREEZE (why the position is on hold)
    ('FREEZE',  'BUDGET_FREEZE',         'Budget freeze',                     10),
    ('FREEZE',  'HIRING_PAUSE',          'Company-wide hiring pause',         20),
    ('FREEZE',  'PENDING_RESTRUCTURE',   'Pending organizational restructure',30),
    ('FREEZE',  'PENDING_APPROVAL',      'Pending senior approval',           40),
    ('FREEZE',  'COMPLIANCE_HOLD',       'Compliance / legal hold',           50),
    ('FREEZE',  'STRATEGIC_REVIEW',      'Strategic review of role',          60),

    -- CLOSURE (why the position is gone for good)
    ('CLOSURE', 'ROLE_OBSOLETE',         'Role no longer needed',             10),
    ('CLOSURE', 'AUTOMATED',             'Replaced by automation',            20),
    ('CLOSURE', 'OUTSOURCED',            'Outsourced',                        30),
    ('CLOSURE', 'MERGED_INTO_ANOTHER',   'Merged into another position',      40),
    ('CLOSURE', 'COST_REDUCTION',        'Cost reduction',                    50),
    ('CLOSURE', 'STRATEGIC_SHIFT',       'Strategic direction shift',         60),
    ('CLOSURE', 'BUSINESS_UNIT_CLOSED',  'Business unit closed',              70),

    -- REPLACEMENT (M246 workflow trigger)
    ('REPLACEMENT', 'INCUMBENT_DEPARTING',    'Incumbent departing',           10),
    ('REPLACEMENT', 'INCUMBENT_PROMOTED',     'Incumbent promoted',            20),
    ('REPLACEMENT', 'INCUMBENT_TRANSFERRED',  'Incumbent transferred',         30),
    ('REPLACEMENT', 'PERFORMANCE_REPLACEMENT','Performance-based replacement', 40),
    ('REPLACEMENT', 'TEMP_TO_PERM',           'Temporary → permanent fill',    50)
ON CONFLICT (category, code) DO NOTHING;
