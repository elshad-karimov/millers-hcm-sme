-- HCM_16 M413 — critical positions + succession plans + approval workflow
-- (PRD §16.2/§16.3/§16.4). Succession plans are DRAFT→SUBMITTED→APPROVED→
-- ACTIVE, approval via existing workflow engine (SUCCESSION_PLAN_APPROVAL
-- definition seeded below, one HR_ADMIN step).

CREATE TABLE IF NOT EXISTS performance.critical_position (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    position_id     UUID NOT NULL,  -- FK staffing.position
    criticality_reason VARCHAR(1000),
    replacement_difficulty VARCHAR(10),  -- LOW | MEDIUM | HIGH
    vacancy_risk    VARCHAR(10),         -- LOW | MEDIUM | HIGH | CRITICAL
    succession_required BOOLEAN NOT NULL DEFAULT true,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_by      VARCHAR(80),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_crit_pos_tenant UNIQUE (tenant_id, position_id),
    CONSTRAINT chk_crit_pos_repl CHECK (replacement_difficulty IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_crit_pos_vac CHECK (vacancy_risk IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);
CREATE INDEX idx_crit_pos_tenant ON performance.critical_position (tenant_id, active);

CREATE TABLE IF NOT EXISTS performance.succession_plan (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    critical_position_id UUID NOT NULL REFERENCES performance.critical_position (id),
    plan_owner_employee_id UUID NOT NULL,  -- HR analyst or talent partner
    effective_date  DATE NOT NULL,
    review_date     DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | SUBMITTED | APPROVED | ACTIVE | ARCHIVED
    emergency_successor_employee_id UUID,  -- immediate replacement (READY_NOW)
    notes           VARCHAR(2000),
    workflow_instance_id UUID,  -- workflow.workflow_instance FK (approval)
    created_by      VARCHAR(80),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_succ_plan_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'ACTIVE', 'ARCHIVED'))
);
CREATE INDEX idx_succ_plan_tenant ON performance.succession_plan (tenant_id, status);
CREATE INDEX idx_succ_plan_crit ON performance.succession_plan (critical_position_id);

COMMENT ON TABLE performance.critical_position IS 'HCM_16 M413 — critical positions (PRD §16.2)';
COMMENT ON TABLE performance.succession_plan IS 'HCM_16 M413 — succession plans + approval (PRD §16.3)';

-- ── Workflow definition: SUCCESSION_PLAN_APPROVAL ──────────────────────────
-- One HR_ADMIN step (resolves_to_manager=false: plan owner varies). Pattern
-- copied from V210 BENEFIT_ENROLLMENT_APPROVAL (M380 — single HR step).

INSERT INTO workflow.workflow_definition (id, code, name, description, active, created_at)
VALUES (
    'f1a0e413-5acc-4001-a001-000000000001'::uuid,
    'SUCCESSION_PLAN_APPROVAL',
    'Succession Plan Approval',
    'HCM_16 M413 — succession plan approval (PRD §16.4). One HR_ADMIN step (plan owner can vary).',
    true,
    NOW()
) ON CONFLICT (code) DO NOTHING;

INSERT INTO workflow.workflow_step_definition (id, definition_id, step_order, step_name, resolver_type, resolver_role, resolves_to_manager, auto_approve, created_at)
VALUES (
    'f1a0e413-5acc-4001-a001-100000000001'::uuid,
    'f1a0e413-5acc-4001-a001-000000000001'::uuid,
    1,
    'HR Review',
    'ROLE',
    'HR_ADMIN',
    false,  -- plan owner varies, no direct manager resolution
    false,
    NOW()
) ON CONFLICT (definition_id, step_order) DO NOTHING;
