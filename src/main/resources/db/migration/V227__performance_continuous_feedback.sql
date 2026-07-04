-- HCM_12 Performance Management — M400 (Phase E.3)
-- Continuous feedback (PRD §22.1) + check-ins (§22.2). Feedback notes are light
-- (praise / improvement / private manager note / request); MANAGER_PRIVATE notes
-- are hidden from the employee in the API. Check-ins carry discussion notes,
-- action items, a follow-up date and employee acknowledgement.

CREATE TABLE performance.continuous_feedback (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          varchar(64) NOT NULL DEFAULT 'default',
    employee_id        uuid NOT NULL,
    author_employee_id uuid,
    kind               varchar(20) NOT NULL DEFAULT 'PRAISE',
    visibility         varchar(20) NOT NULL DEFAULT 'EMPLOYEE_VISIBLE',
    message            varchar(2000) NOT NULL,
    tags               varchar(300),
    created_by         varchar(80),
    created_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT cont_feedback_kind_check CHECK (kind IN
        ('PRAISE','IMPROVEMENT','NOTE','REQUEST')),
    CONSTRAINT cont_feedback_visibility_check CHECK (visibility IN
        ('EMPLOYEE_VISIBLE','MANAGER_PRIVATE'))
);
CREATE INDEX cont_feedback_employee_idx
    ON performance.continuous_feedback (tenant_id, employee_id, created_at DESC);

CREATE TABLE performance.perf_check_in (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        varchar(64) NOT NULL DEFAULT 'default',
    employee_id      uuid NOT NULL,
    manager_id       uuid,
    meeting_date     date NOT NULL,
    kind             varchar(20) NOT NULL DEFAULT 'ONE_TO_ONE',
    discussion_notes text,
    action_items     text,
    follow_up_date   date,
    acknowledged_at  timestamptz,
    created_by       varchar(80),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT check_in_kind_check CHECK (kind IN ('ONE_TO_ONE','MONTHLY','QUARTERLY'))
);
CREATE INDEX check_in_employee_idx
    ON performance.perf_check_in (tenant_id, employee_id, meeting_date DESC);

COMMENT ON TABLE performance.continuous_feedback IS
    'HCM_12 M400 — §22.1 lightweight ongoing feedback; MANAGER_PRIVATE hidden from the employee.';
COMMENT ON TABLE performance.perf_check_in IS
    'HCM_12 M400 — §22.2 one-to-one / monthly / quarterly check-ins with employee acknowledgement.';
