-- HCM_14 LMS — M404 (Phase B.1)
-- Instructor management (PRD 14 §18) + training rooms/venues. An instructor is
-- an internal employee (employee_id) or an external trainer (external_name);
-- hourly cost feeds M407 cost tracking, rating is aggregated from M408 feedback.

CREATE TABLE learning.instructor (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      varchar(64) NOT NULL DEFAULT 'default',
    employee_id    uuid,
    external_name  varchar(200),
    email          varchar(200),
    qualifications varchar(500),
    hourly_cost    numeric(12,2),
    rating         numeric(3,2),
    active         boolean NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT instructor_identity_check CHECK (employee_id IS NOT NULL OR external_name IS NOT NULL)
);
CREATE INDEX instructor_tenant_idx ON learning.instructor (tenant_id, active);

CREATE TABLE learning.training_room (
    id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default',
    code      varchar(40) NOT NULL,
    name      varchar(200) NOT NULL,
    location  varchar(300),
    capacity  int,
    virtual   boolean NOT NULL DEFAULT false,
    notes     varchar(500),
    active    boolean NOT NULL DEFAULT true,
    CONSTRAINT training_room_code_unique UNIQUE (tenant_id, code)
);

COMMENT ON TABLE learning.instructor IS
    'HCM_14 M404 — internal (employee) or external trainer (PRD 14 §18).';
COMMENT ON TABLE learning.training_room IS
    'HCM_14 M404 — classroom / virtual venue with capacity.';
