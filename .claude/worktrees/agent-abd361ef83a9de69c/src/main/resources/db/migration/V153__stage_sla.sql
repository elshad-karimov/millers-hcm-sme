-- ----------------------------------------------------------------------------
-- M288 — Recruitment PRD §14 + §43: pipeline stage SLA + overdue tracking.
--
-- The canonical pipeline stays the ApplicationStage enum (referenced
-- across apply / transition / offer / hire / funnel) — this milestone
-- adds a per-stage SLA (in days) + optional owner role, and computes
-- time-in-stage from the existing ApplicationEvent timeline (M45) to
-- flag overdue applications. No per-vacancy stage customization yet —
-- that's a documented later seam.
--
-- Seeded with the PRD §43 example SLAs.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS recruitment.stage_sla (
    id          UUID PRIMARY KEY,
    stage       VARCHAR(32)  NOT NULL UNIQUE,   -- ApplicationStage enum value
    sla_days    INT          NOT NULL,
    owner_role  VARCHAR(40),                    -- who is accountable in this stage
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(120),
    CONSTRAINT chk_stage_sla_days CHECK (sla_days > 0)
);

INSERT INTO recruitment.stage_sla (id, stage, sla_days, owner_role)
VALUES
    ('5a1a0001-0000-0000-0000-000000000001', 'CV_SCREENING',        3, 'RECRUITER'),
    ('5a1a0001-0000-0000-0000-000000000002', 'HR_INTERVIEW',        5, 'HR_SPECIALIST'),
    ('5a1a0001-0000-0000-0000-000000000003', 'TECHNICAL_INTERVIEW', 5, 'DEPARTMENT_MANAGER'),
    ('5a1a0001-0000-0000-0000-000000000004', 'FINAL_INTERVIEW',     3, 'DEPARTMENT_MANAGER'),
    ('5a1a0001-0000-0000-0000-000000000005', 'OFFER',               3, 'HR_ADMIN')
ON CONFLICT (stage) DO NOTHING;
