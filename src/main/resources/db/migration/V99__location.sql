-- M141 — Location master (§11 audit closure).
--
-- Until now OrgUnit, Position, and Employee all carried a free-text
-- VARCHAR(200) "location" column — no time-zone, no GPS, no calendar
-- linkage, no device binding. This migration adds the structured master
-- and wires it in via nullable FKs on org_unit and employee.
--
-- The existing free-text columns are intentionally left in place so
-- existing rows keep rendering. New code should prefer the FK.

CREATE TABLE IF NOT EXISTS organization.location (
    id                      UUID         PRIMARY KEY,
    code                    VARCHAR(60)  NOT NULL UNIQUE,
    name                    VARCHAR(200) NOT NULL,
    -- §11 categorisation (HEAD_OFFICE / BRANCH / STORE / WAREHOUSE /
    -- FACTORY / RESTAURANT / DISTRIBUTION_CENTER / REMOTE /
    -- CLIENT_SITE / PROJECT_SITE).
    location_type           VARCHAR(40)  NOT NULL
                                CHECK (location_type IN (
                                    'HEAD_OFFICE','BRANCH','STORE','WAREHOUSE',
                                    'FACTORY','RESTAURANT','DISTRIBUTION_CENTER',
                                    'REMOTE','CLIENT_SITE','PROJECT_SITE')),

    -- Physical address fields.
    country                 VARCHAR(2)
                                CHECK (country IS NULL
                                    OR (length(country) = 2 AND country = upper(country))),
    city                    VARCHAR(120),
    region                  VARCHAR(120),
    address                 VARCHAR(500),

    -- Geolocation — decimal degrees, WGS-84.
    latitude                DECIMAL(9,6)
                                CHECK (latitude IS NULL
                                    OR (latitude BETWEEN -90 AND 90)),
    longitude               DECIMAL(9,6)
                                CHECK (longitude IS NULL
                                    OR (longitude BETWEEN -180 AND 180)),

    -- IANA time-zone ID (e.g. "Asia/Baku", "Europe/London").
    timezone                VARCHAR(60),

    -- Links to the existing core_hr.holiday table's jurisdiction column
    -- (e.g. "AZ", "GB"). NULL = use the system default.
    holiday_jurisdiction    VARCHAR(8),

    -- Opaque code pointer to a work-calendar master (future).
    work_calendar_code      VARCHAR(60),

    -- Soft FK to attendance.shift_group — NULL = no default.
    default_shift_group_id  UUID,

    -- Soft FK to the location's designated site manager.
    branch_manager_id       UUID,

    -- Soft FK to organization.legal_entity — drives payroll allowances,
    -- tax rules, and statutory reports for the site.
    legal_entity_id         UUID
                                REFERENCES organization.legal_entity (id)
                                ON DELETE SET NULL,

    cost_centre_code        VARCHAR(64),
    phone                   VARCHAR(32),
    email                   VARCHAR(160),

    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    notes                   TEXT,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(80),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by              VARCHAR(80),

    CONSTRAINT chk_loc_gps_pair
        CHECK ((latitude IS NULL) = (longitude IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_location_active
    ON organization.location (active);
CREATE INDEX IF NOT EXISTS idx_location_legal_entity
    ON organization.location (legal_entity_id)
    WHERE legal_entity_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_location_type
    ON organization.location (location_type);

-- ── Link from org_unit ────────────────────────────────────────────────
-- Replaces the free-text VARCHAR(200) location column with a proper FK.
-- The old column is kept (nullable) so existing data is not lost; the
-- service layer prefers the FK when set.

ALTER TABLE organization.org_unit
    ADD COLUMN IF NOT EXISTS location_id UUID
        REFERENCES organization.location (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_org_unit_location
    ON organization.org_unit (location_id)
    WHERE location_id IS NOT NULL;

-- ── Link from employee ────────────────────────────────────────────────
-- Primary work location — drives geofencing, payroll allowances,
-- shift defaults, and on-site attendance rules.

ALTER TABLE core_hr.employee
    ADD COLUMN IF NOT EXISTS work_location_id UUID
        REFERENCES organization.location (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_employee_work_location
    ON core_hr.employee (work_location_id)
    WHERE work_location_id IS NOT NULL;
