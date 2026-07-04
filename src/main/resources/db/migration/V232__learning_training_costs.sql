-- HCM_14 LMS — M407 (Phase B.4)
-- Training cost tracking (PRD 14 §19): typed cost lines against a course or a
-- classroom session (single-currency AZN per scope decision).

CREATE TABLE learning.training_cost (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   varchar(64) NOT NULL DEFAULT 'default',
    course_id   uuid REFERENCES learning.course (id),
    session_id  uuid REFERENCES learning.training_session (id),
    cost_type   varchar(20) NOT NULL DEFAULT 'OTHER',
    description varchar(300),
    amount      numeric(14,2) NOT NULL,
    currency    varchar(3) NOT NULL DEFAULT 'AZN',
    incurred_on date,
    created_by  varchar(80),
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT training_cost_target_check CHECK (course_id IS NOT NULL OR session_id IS NOT NULL),
    CONSTRAINT training_cost_type_check CHECK (cost_type IN
        ('INSTRUCTOR','VENUE','MATERIAL','TRAVEL','CATERING','OTHER')),
    CONSTRAINT training_cost_amount_check CHECK (amount >= 0)
);
CREATE INDEX training_cost_course_idx ON learning.training_cost (tenant_id, course_id);
CREATE INDEX training_cost_session_idx ON learning.training_cost (session_id);

COMMENT ON TABLE learning.training_cost IS
    'HCM_14 M407 — typed training cost line per course/session (PRD 14 §19).';
