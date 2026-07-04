-- HCM_12 Performance Management — M399 (Phase E.2)
-- Development plans (PRD §21): performance-linked growth plans with typed actions
-- (§21.2) and an optional competency-gap link (M393 feeds this — §21.3). The M95
-- learning-path assignment remains the LMS-side IDP; course-typed actions carry an
-- optional course link as the enrolment seam.

CREATE TABLE performance.development_plan (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          varchar(64) NOT NULL DEFAULT 'default',
    employee_id        uuid NOT NULL,
    review_id          uuid REFERENCES performance.performance_review (id),
    competency_id      uuid,
    career_goal        varchar(300),
    title              varchar(300) NOT NULL,
    description        text,
    target_date        date,
    owner_employee_id  uuid,
    status             varchar(20) NOT NULL DEFAULT 'DRAFT',
    progress_percent   numeric(5,2) NOT NULL DEFAULT 0,
    manager_comments   varchar(2000),
    employee_comments  varchar(2000),
    created_by         varchar(80),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT dev_plan_status_check CHECK (status IN
        ('DRAFT','ACTIVE','IN_PROGRESS','COMPLETED','CANCELLED'))
);
CREATE INDEX dev_plan_employee_idx ON performance.development_plan (tenant_id, employee_id);

CREATE TABLE performance.development_action (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    varchar(64) NOT NULL DEFAULT 'default',
    plan_id      uuid NOT NULL REFERENCES performance.development_plan (id) ON DELETE CASCADE,
    action_type  varchar(30) NOT NULL DEFAULT 'TRAINING_COURSE',
    description  varchar(1000) NOT NULL,
    course_id    uuid,
    due_date     date,
    status       varchar(20) NOT NULL DEFAULT 'PLANNED',
    completed_at timestamptz,
    CONSTRAINT dev_action_type_check CHECK (action_type IN
        ('TRAINING_COURSE','CERTIFICATION','COACHING','MENTORING','JOB_ROTATION',
         'STRETCH_ASSIGNMENT','PROJECT_ASSIGNMENT','SELF_STUDY','WORKSHOP','LEADERSHIP_PROGRAM')),
    CONSTRAINT dev_action_status_check CHECK (status IN
        ('PLANNED','IN_PROGRESS','DONE','CANCELLED'))
);
CREATE INDEX dev_action_plan_idx ON performance.development_action (plan_id);

COMMENT ON TABLE performance.development_plan IS
    'HCM_12 M399 — §21 development plan; progress = done actions / live actions.';
