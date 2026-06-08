-- M143 — Configurable OrgUnitType (§5)
-- Replaces the hardcoded Java enum with a DB-backed config table so
-- operators can add, rename, recolour, or retire org-unit types at
-- runtime without a code deploy.

CREATE TABLE organization.org_unit_type (
    code                  varchar(64)  PRIMARY KEY,
    label                 varchar(200) NOT NULL,
    color                 varchar(7),                -- hex e.g. #1677ff
    sort_order            int          NOT NULL DEFAULT 0,
    can_have_children     boolean      NOT NULL DEFAULT true,
    is_root_level         boolean      NOT NULL DEFAULT false,
    -- JSON array of allowed parent type codes; NULL = any parent allowed.
    allowed_parent_types  jsonb,
    active                boolean      NOT NULL DEFAULT true,
    notes                 text,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now()
);

-- Seed the seven types that previously existed as the Java enum.
-- Color palette: Ant Design primary token colours per level depth.
INSERT INTO organization.org_unit_type
    (code, label, color, sort_order, can_have_children, is_root_level)
VALUES
    ('COMPANY',             'Company',             '#1677ff', 0, true,  true),
    ('BRANCH',              'Branch',              '#52c41a', 1, true,  false),
    ('DIVISION',            'Division',            '#722ed1', 2, true,  false),
    ('DEPARTMENT',          'Department',          '#fa8c16', 3, true,  false),
    ('SECTION',             'Section',             '#13c2c2', 4, true,  false),
    ('UNIT',                'Unit',                '#eb2f96', 5, true,  false),
    ('TEAM',                'Team',                '#faad14', 6, false, false);
