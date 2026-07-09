-- ----------------------------------------------------------------------------
-- M456 — HCM_29 Phase A.1: Asset Category catalog (tenant-specific).
--
-- Replaces the hardcoded AssetType enum with a tenant-configurable catalog.
-- EmployeeAsset.category_id FK (nullable) preserves the existing enum for
-- backward compatibility. New assignments should reference a category row.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS lifecycle.asset_category (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               varchar(80)  NOT NULL DEFAULT 'default',
    code                    varchar(60)  NOT NULL,
    name                    varchar(200) NOT NULL,
    description             text,
    default_depreciation_method varchar(40),
    default_useful_life_years   int,
    active                  boolean      NOT NULL DEFAULT true,
    created_at              timestamptz  NOT NULL DEFAULT now(),
    created_by              varchar(80),
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    updated_by              varchar(80),
    CONSTRAINT asset_category_uq UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS asset_category_tenant_idx ON lifecycle.asset_category (tenant_id);
CREATE INDEX IF NOT EXISTS asset_category_active_idx ON lifecycle.asset_category (tenant_id, active);

COMMENT ON TABLE lifecycle.asset_category IS
    'M456 — Tenant-specific asset category master. Replaces hardcoded AssetType enum.';

-- ── Add category_id FK to employee_asset (nullable for backward compat) ────
ALTER TABLE core_hr.employee_asset
    ADD COLUMN IF NOT EXISTS category_id uuid REFERENCES lifecycle.asset_category(id);

CREATE INDEX IF NOT EXISTS employee_asset_category_idx ON core_hr.employee_asset (category_id);

COMMENT ON COLUMN core_hr.employee_asset.category_id IS
    'M456 — FK to asset_category. Nullable for backward compatibility with AssetType enum. New assignments should populate this.';

-- ── Seed default categories matching existing AssetType enum ───────────────
INSERT INTO lifecycle.asset_category (tenant_id, code, name, description, active)
VALUES
    ('default', 'LAPTOP', 'Laptop', 'Laptop computer', true),
    ('default', 'DESKTOP', 'Desktop Computer', 'Desktop computer', true),
    ('default', 'MONITOR', 'Monitor', 'Computer monitor', true),
    ('default', 'PHONE', 'Mobile Phone', 'Mobile phone', true),
    ('default', 'TABLET', 'Tablet', 'Tablet device', true),
    ('default', 'VEHICLE', 'Vehicle', 'Company vehicle', true),
    ('default', 'ACCESS_CARD', 'Access Card', 'Physical access card', true),
    ('default', 'UNIFORM', 'Uniform', 'Company uniform', true),
    ('default', 'TOOLS', 'Tools', 'Work tools and equipment', true),
    ('default', 'KEYS', 'Keys', 'Office/facility keys', true),
    ('default', 'OTHER', 'Other', 'Other asset type', true)
ON CONFLICT (tenant_id, code) DO NOTHING;
