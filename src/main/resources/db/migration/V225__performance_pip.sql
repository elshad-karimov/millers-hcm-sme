-- HCM_12 Performance Management — M398 (Phase E.1)
-- Performance Improvement Plans (PRD §20): manual creation (gap-check decision),
-- employee acknowledgement, periodic checkpoints with progress ratings, extension,
-- and a controlled outcome. Evidence files reuse the M16 attachment registry
-- (owner_entity = 'pip' / 'pipcheckpoint') — no schema needed here.

CREATE TABLE performance.performance_pip (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             varchar(64) NOT NULL DEFAULT 'default',
    employee_id           uuid NOT NULL,
    review_id             uuid REFERENCES performance.performance_review (id),
    manager_owner_id      uuid,
    hr_owner              varchar(80),
    reason                varchar(2000) NOT NULL,
    issue_description     text,
    objectives            text,
    success_criteria      text,
    support_actions       text,
    consequences          text,
    start_date            date NOT NULL,
    end_date              date NOT NULL,
    status                varchar(24) NOT NULL DEFAULT 'DRAFT',
    outcome               varchar(30),
    outcome_notes         varchar(2000),
    acknowledged_at       timestamptz,
    acknowledged_comments varchar(2000),
    created_by            varchar(80),
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pip_dates_check CHECK (end_date >= start_date),
    CONSTRAINT pip_status_check CHECK (status IN
        ('DRAFT','ACTIVE','ACKNOWLEDGED','IN_PROGRESS','EXTENDED',
         'COMPLETED_SUCCESS','FAILED','CANCELLED')),
    CONSTRAINT pip_outcome_check CHECK (outcome IS NULL OR outcome IN
        ('IMPROVED','EXTENDED','ROLE_CHANGE','TRAINING_REQUIRED',
         'DISCIPLINARY','TERMINATION_RECOMMENDED'))
);
CREATE INDEX pip_employee_idx ON performance.performance_pip (tenant_id, employee_id);
CREATE INDEX pip_status_idx ON performance.performance_pip (tenant_id, status);

CREATE TABLE performance.pip_checkpoint (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         varchar(64) NOT NULL DEFAULT 'default',
    pip_id            uuid NOT NULL REFERENCES performance.performance_pip (id) ON DELETE CASCADE,
    checkpoint_date   date NOT NULL,
    progress_rating   int,
    manager_comments  varchar(2000),
    employee_comments varchar(2000),
    recorded_by       varchar(80),
    recorded_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pip_checkpoint_rating_check CHECK (progress_rating IS NULL OR progress_rating BETWEEN 1 AND 5)
);
CREATE INDEX pip_checkpoint_pip_idx ON performance.pip_checkpoint (pip_id);

COMMENT ON TABLE performance.performance_pip IS
    'HCM_12 M398 — performance improvement plan (§20); outcome never deletes history.';
