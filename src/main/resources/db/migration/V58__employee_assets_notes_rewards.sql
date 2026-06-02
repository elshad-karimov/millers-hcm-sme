-- M72 / Phase 2 — Employee assets, notes, and reward records
-- (P2-09 + P2-10 + P2-18, HIGH / MEDIUM priority)
--
--   * employee_asset       — company property assigned to an employee
--                            (laptop, phone, vehicle, access card, etc.).
--                            Tracks assign / return lifecycle with condition
--                            snapshots at both ends, custody form attachment,
--                            and expected vs. actual return dates.
--
--   * employee_note        — HR / manager / system notes about an employee
--                            with a visibility_level column gating who can
--                            read each row. Sensitive notes (HR_ONLY,
--                            CONFIDENTIAL) are filtered in the service layer,
--                            not the DB — the schema only enforces the enum.
--
--   * employee_reward      — recognition / award records: monetary awards,
--                            certificates, fast-track promotions. Standalone
--                            from the payroll bonus engine (those live in
--                            payroll.payroll_bonus).

-- ── employee_asset ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core_hr.employee_asset (
    id                      UUID         PRIMARY KEY,
    employee_id             UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    asset_type              VARCHAR(20)  NOT NULL,
    asset_identifier        VARCHAR(120),
    asset_name              VARCHAR(160) NOT NULL,
    description             TEXT,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ASSIGNED',
    assigned_at             DATE         NOT NULL,
    expected_return_date    DATE,
    returned_at             DATE,
    condition_at_assignment VARCHAR(40),
    condition_at_return     VARCHAR(40),
    return_accepted_by      VARCHAR(80),
    custody_form_url        VARCHAR(500),
    notes                   TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(80),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by              VARCHAR(80),
    CONSTRAINT chk_asset_type CHECK (asset_type IN (
        'LAPTOP','MOBILE_PHONE','SIM_CARD','VEHICLE','ACCESS_CARD',
        'UNIFORM','TOOLS','EQUIPMENT','LOCKER','FUEL_CARD','CREDIT_CARD',
        'TABLET','HEADSET','OTHER'
    )),
    CONSTRAINT chk_asset_status CHECK (status IN ('ASSIGNED','RETURNED','LOST','DAMAGED','WRITTEN_OFF')),
    CONSTRAINT chk_asset_dates CHECK (returned_at IS NULL OR returned_at >= assigned_at),
    CONSTRAINT chk_asset_return_status CHECK (
        -- A status outside ASSIGNED requires a returned_at timestamp
        status = 'ASSIGNED' OR returned_at IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS idx_asset_employee
    ON core_hr.employee_asset (employee_id, status);

CREATE INDEX IF NOT EXISTS idx_asset_status_active
    ON core_hr.employee_asset (status)
    WHERE status = 'ASSIGNED';


-- ── employee_note ────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core_hr.employee_note (
    id                  UUID         PRIMARY KEY,
    employee_id         UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    note_type           VARCHAR(20)  NOT NULL DEFAULT 'GENERAL',
    note_body           TEXT         NOT NULL,
    -- visibility_level determines who can read this note in the service layer:
    --   ALL_HR            — any HR role or DEPARTMENT_MANAGER on own reports
    --   MANAGER_ONLY      — DEPARTMENT_MANAGER + HR_* + SYSTEM_ADMIN
    --   HR_ONLY           — HR_* + SYSTEM_ADMIN only
    --   SYSTEM_ADMIN_ONLY — SYSTEM_ADMIN only
    visibility_level    VARCHAR(30)  NOT NULL DEFAULT 'ALL_HR',
    pinned              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(80),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          VARCHAR(80),
    CONSTRAINT chk_note_type CHECK (note_type IN (
        'GENERAL','CONFIDENTIAL','MANAGER','HR','PERFORMANCE','PAYROLL','SYSTEM'
    )),
    CONSTRAINT chk_note_visibility CHECK (visibility_level IN (
        'ALL_HR','MANAGER_ONLY','HR_ONLY','SYSTEM_ADMIN_ONLY'
    ))
);

CREATE INDEX IF NOT EXISTS idx_note_employee
    ON core_hr.employee_note (employee_id, created_at DESC);


-- ── employee_reward ──────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core_hr.employee_reward (
    id                  UUID         PRIMARY KEY,
    employee_id         UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    reward_type         VARCHAR(30)  NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    award_value         NUMERIC(12,2),
    currency            VARCHAR(3),
    awarded_by          VARCHAR(80),
    awarded_at          DATE         NOT NULL,
    certificate_url     VARCHAR(500),
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(80),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          VARCHAR(80),
    CONSTRAINT chk_reward_type CHECK (reward_type IN (
        'RECOGNITION','MONETARY_AWARD','CERTIFICATE','APPRECIATION_LETTER',
        'PROMOTION_FAST_TRACK','BONUS_RECOMMENDATION','ACHIEVEMENT','SPOT_AWARD','OTHER'
    )),
    CONSTRAINT chk_reward_value CHECK (award_value IS NULL OR award_value >= 0),
    CONSTRAINT chk_reward_currency CHECK (
        currency IS NULL OR (length(currency) = 3 AND currency = upper(currency))
    )
);

CREATE INDEX IF NOT EXISTS idx_reward_employee
    ON core_hr.employee_reward (employee_id, awarded_at DESC);
