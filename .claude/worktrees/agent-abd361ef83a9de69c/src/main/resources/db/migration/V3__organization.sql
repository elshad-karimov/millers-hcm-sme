-- Organizational Structure module (PRD Section 8.2).
-- Versioned hierarchy with rollback and an approval state machine.

CREATE SEQUENCE organization.structure_version_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE organization.structure_version (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    version_number       INTEGER      NOT NULL UNIQUE,
    effective_date       DATE         NOT NULL,

    -- DRAFT, PENDING_APPROVAL, APPROVED, ACTIVE, REJECTED, ARCHIVED  (PRD 8.2.2)
    status               VARCHAR(32)  NOT NULL,
    change_reason        TEXT,
    previous_version_id  UUID         REFERENCES organization.structure_version (id),

    created_by           VARCHAR(120) NOT NULL,
    approved_by          VARCHAR(120),
    activated_at         TIMESTAMPTZ,
    archived_at          TIMESTAMPTZ,

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- At most one ACTIVE version at any time.
CREATE UNIQUE INDEX uniq_one_active_structure_version
    ON organization.structure_version (status)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_structure_version_status ON organization.structure_version (status);

CREATE TABLE organization.org_unit (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id        UUID         NOT NULL REFERENCES organization.structure_version (id) ON DELETE CASCADE,

    code              VARCHAR(64)  NOT NULL,
    name              VARCHAR(200) NOT NULL,

    -- COMPANY, BRANCH, DIVISION, DEPARTMENT, SECTION, UNIT, TEAM  (PRD 8.2.1)
    unit_type         VARCHAR(32)  NOT NULL,
    parent_id         UUID         REFERENCES organization.org_unit (id) ON DELETE CASCADE,
    head_employee_id  UUID,
    sort_order        INTEGER      NOT NULL DEFAULT 0,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (version_id, code)
);

CREATE INDEX idx_org_unit_version   ON organization.org_unit (version_id);
CREATE INDEX idx_org_unit_parent    ON organization.org_unit (parent_id);
CREATE INDEX idx_org_unit_type      ON organization.org_unit (unit_type);
