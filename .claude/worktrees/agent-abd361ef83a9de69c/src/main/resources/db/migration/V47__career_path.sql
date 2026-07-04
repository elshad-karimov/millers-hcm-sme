-- M57: Career-path / Individual Development Plan (IDP)
-- Schema: learning (reuses the existing LMS schema for competency linkage)

-- Individual Development Plan — one per employee at a time (active)
CREATE TABLE learning.idp (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     UUID         NOT NULL REFERENCES core_hr.employee(id),
    target_role     VARCHAR(200) NOT NULL,
    target_date     DATE,
    status          VARCHAR(30)  NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT','ACTIVE','COMPLETED','CANCELLED')),
    manager_comment TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ON learning.idp (employee_id);

-- Skill gaps — competency entries the employee wants to grow
CREATE TABLE learning.idp_skill_gap (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    idp_id          UUID         NOT NULL REFERENCES learning.idp(id) ON DELETE CASCADE,
    competency_id   UUID         REFERENCES learning.competency(id),
    skill_name      VARCHAR(200) NOT NULL,
    current_level   SMALLINT     CHECK (current_level BETWEEN 1 AND 5),
    target_level    SMALLINT     CHECK (target_level  BETWEEN 1 AND 5),
    notes           TEXT
);
CREATE INDEX ON learning.idp_skill_gap (idp_id);

-- Training activities — actions the employee will take to close gaps
CREATE TABLE learning.idp_activity (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    idp_id          UUID         NOT NULL REFERENCES learning.idp(id) ON DELETE CASCADE,
    title           VARCHAR(300) NOT NULL,
    activity_type   VARCHAR(50)  NOT NULL DEFAULT 'COURSE'
                    CHECK (activity_type IN ('COURSE','MENTORING','PROJECT','CONFERENCE','READING','OTHER')),
    course_id       UUID         REFERENCES learning.course(id),
    due_date        DATE,
    status          VARCHAR(30)  NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','IN_PROGRESS','DONE','SKIPPED')),
    completed_at    DATE,
    notes           TEXT
);
CREATE INDEX ON learning.idp_activity (idp_id);
