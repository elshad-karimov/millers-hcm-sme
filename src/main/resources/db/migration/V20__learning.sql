-- Learning Management System (PRD Section 8.14).
-- Tables: course (with markdown content), quiz_question (multiple-choice with
-- choices_json), enrollment (per employee+course with status + score),
-- quiz_attempt + quiz_answer (audit trail per attempt), certificate
-- (auto-issued on PASSED), competency + course_competency +
-- employee_competency (catalogue + awards).

CREATE SEQUENCE learning.course_no_seq      START WITH 1;
CREATE SEQUENCE learning.enrollment_no_seq  START WITH 1;
CREATE SEQUENCE learning.certificate_no_seq START WITH 1;

CREATE TABLE learning.course (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    course_no         VARCHAR(32)  NOT NULL UNIQUE,
    code              VARCHAR(64)  NOT NULL UNIQUE,
    title             VARCHAR(240) NOT NULL,
    description       TEXT,
    -- Body is plain markdown (rendered client-side). For multi-module courses
    -- a follow-up migration can introduce course_module; this slice keeps it flat.
    content_markdown  TEXT,
    -- COMPLIANCE, ONBOARDING, TECHNICAL, LEADERSHIP, SOFT_SKILLS, OTHER
    category          VARCHAR(32)  NOT NULL,
    duration_hours    NUMERIC(6,2) NOT NULL DEFAULT 1,
    mandatory         BOOLEAN      NOT NULL DEFAULT FALSE,
    passing_score     INTEGER      NOT NULL DEFAULT 70,   -- percent
    max_attempts      INTEGER      NOT NULL DEFAULT 3,
    -- DRAFT, PUBLISHED, ARCHIVED  (PRD 8.14.2)
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    instructor_id     UUID,                          -- soft ref into core_hr.employee
    valid_for_months  INTEGER,                       -- certificate validity, NULL = no expiry
    cover_url         VARCHAR(500),
    published_at      TIMESTAMPTZ,
    archived_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(120),
    updated_by        VARCHAR(120),

    CONSTRAINT chk_course_passing CHECK (passing_score BETWEEN 0 AND 100),
    CONSTRAINT chk_course_attempts CHECK (max_attempts >= 1)
);

CREATE INDEX idx_course_status   ON learning.course (status);
CREATE INDEX idx_course_category ON learning.course (category);
CREATE INDEX idx_course_mandatory ON learning.course (mandatory) WHERE mandatory = TRUE;

CREATE TABLE learning.quiz_question (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id       UUID         NOT NULL REFERENCES learning.course (id) ON DELETE CASCADE,
    question_no     INTEGER      NOT NULL,                 -- order within the course
    -- MULTIPLE_CHOICE (single correct), MULTI_SELECT (multiple correct), TRUE_FALSE
    question_type   VARCHAR(16)  NOT NULL DEFAULT 'MULTIPLE_CHOICE',
    question_text   TEXT         NOT NULL,
    -- choices_json: array of {"key":"A","text":"…"} entries
    choices_json    JSONB        NOT NULL,
    -- correct_keys: array of choice keys, e.g. ["B"] or ["A","C"]
    correct_keys    JSONB        NOT NULL,
    explanation     TEXT,
    points          INTEGER      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (course_id, question_no),
    CONSTRAINT chk_qq_points CHECK (points > 0)
);

CREATE INDEX idx_qq_course ON learning.quiz_question (course_id, question_no);

CREATE TABLE learning.enrollment (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_no      VARCHAR(32)  NOT NULL UNIQUE,
    course_id          UUID         NOT NULL REFERENCES learning.course (id),
    employee_id        UUID         NOT NULL,
    -- ENROLLED, IN_PROGRESS, PASSED, FAILED, WITHDRAWN, EXPIRED
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENROLLED',
    enrolled_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at         TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    due_date           DATE,
    attempts_used      INTEGER      NOT NULL DEFAULT 0,
    best_score_percent NUMERIC(5,2),
    last_score_percent NUMERIC(5,2),
    last_attempt_at    TIMESTAMPTZ,
    -- ASSIGNED (by HR/manager) vs SELF_ENROLLED
    enrolled_via       VARCHAR(16)  NOT NULL DEFAULT 'SELF_ENROLLED',
    assigned_by        VARCHAR(120),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (course_id, employee_id),
    CONSTRAINT chk_enroll_attempts CHECK (attempts_used >= 0)
);

CREATE INDEX idx_enroll_employee ON learning.enrollment (employee_id);
CREATE INDEX idx_enroll_course   ON learning.enrollment (course_id);
CREATE INDEX idx_enroll_status   ON learning.enrollment (status);

CREATE TABLE learning.quiz_attempt (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id   UUID         NOT NULL REFERENCES learning.enrollment (id) ON DELETE CASCADE,
    attempt_no      INTEGER      NOT NULL,
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    submitted_at    TIMESTAMPTZ,
    -- IN_PROGRESS, PASSED, FAILED
    status          VARCHAR(16)  NOT NULL DEFAULT 'IN_PROGRESS',
    total_points    INTEGER,
    earned_points   INTEGER,
    score_percent   NUMERIC(5,2),
    -- denormalised for quick lookup
    passing_score   INTEGER,

    UNIQUE (enrollment_id, attempt_no)
);

CREATE INDEX idx_attempt_enrollment ON learning.quiz_attempt (enrollment_id);

CREATE TABLE learning.quiz_answer (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id      UUID         NOT NULL REFERENCES learning.quiz_attempt (id) ON DELETE CASCADE,
    question_id     UUID         NOT NULL REFERENCES learning.quiz_question (id),
    -- selected_keys: array of chosen choice keys
    selected_keys   JSONB        NOT NULL,
    correct         BOOLEAN      NOT NULL,
    points_awarded  INTEGER      NOT NULL DEFAULT 0,

    UNIQUE (attempt_id, question_id)
);

CREATE TABLE learning.certificate (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    certificate_no  VARCHAR(32)  NOT NULL UNIQUE,
    enrollment_id   UUID         NOT NULL UNIQUE REFERENCES learning.enrollment (id) ON DELETE CASCADE,
    course_id       UUID         NOT NULL REFERENCES learning.course (id),
    employee_id     UUID         NOT NULL,
    issued_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    valid_until     DATE,
    score_percent   NUMERIC(5,2) NOT NULL,
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMPTZ,
    revoked_by      VARCHAR(120),
    revoked_reason  TEXT
);

CREATE INDEX idx_cert_employee ON learning.certificate (employee_id);
CREATE INDEX idx_cert_course   ON learning.certificate (course_id);

-- Competency framework (PRD 8.14.5 — minimal slice for course mapping).
CREATE TABLE learning.competency (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(64)  NOT NULL UNIQUE,
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    -- TECHNICAL, BEHAVIOURAL, LEADERSHIP, COMPLIANCE, LANGUAGE, OTHER
    category      VARCHAR(32)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE learning.course_competency (
    course_id      UUID NOT NULL REFERENCES learning.course (id) ON DELETE CASCADE,
    competency_id  UUID NOT NULL REFERENCES learning.competency (id) ON DELETE CASCADE,
    -- Proficiency level granted on course completion: 1 (Awareness) .. 5 (Expert)
    awarded_level  INTEGER NOT NULL DEFAULT 3,
    PRIMARY KEY (course_id, competency_id),
    CONSTRAINT chk_cc_level CHECK (awarded_level BETWEEN 1 AND 5)
);

CREATE TABLE learning.employee_competency (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     UUID         NOT NULL,
    competency_id   UUID         NOT NULL REFERENCES learning.competency (id),
    proficiency     INTEGER      NOT NULL,
    source          VARCHAR(32)  NOT NULL,    -- COURSE, MANUAL, IMPORT
    source_ref      UUID,                       -- enrollment_id when source=COURSE
    awarded_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    valid_until     DATE,

    UNIQUE (employee_id, competency_id, source_ref),
    CONSTRAINT chk_ec_proficiency CHECK (proficiency BETWEEN 1 AND 5)
);

CREATE INDEX idx_ec_employee ON learning.employee_competency (employee_id);

-- ---------- Seed data ----------

-- Two example competencies linked to the seeded compliance course.
INSERT INTO learning.competency (id, code, name, description, category, active) VALUES
    ('e1111111-1111-1111-1111-111111111111', 'INFOSEC_AWARENESS', 'Information Security Awareness',
     'Recognises phishing, secure passwords, data classification, incident reporting.',
     'COMPLIANCE', TRUE),
    ('e2222222-2222-2222-2222-222222222222', 'GDPR_BASICS', 'GDPR Basics',
     'Knows lawful basis, data subject rights, breach reporting timelines, PII handling.',
     'COMPLIANCE', TRUE);

-- One sample compliance course with 4 quiz questions and 2 mapped competencies.
INSERT INTO learning.course
  (id, course_no, code, title, description, content_markdown, category, duration_hours,
   mandatory, passing_score, max_attempts, status, valid_for_months, published_at, created_by)
VALUES (
  'a1111111-1111-1111-1111-111111111111',
  'CRS-00001',
  'INFOSEC-101',
  'Information Security Essentials',
  'Mandatory annual course covering phishing, passwords, data classification and incident reporting.',
  $$# Information Security Essentials

## Why this matters
A single compromised credential or careless click can expose the entire company.
This course gives you the working knowledge to spot the common attacks and act fast.

## Topics
1. **Phishing & social engineering** — what to look for in suspicious emails.
2. **Strong passwords & MFA** — pass-phrases, password managers, MFA fatigue attacks.
3. **Data classification** — Public / Internal / Confidential / Restricted.
4. **Incident reporting** — who to contact, what to capture, what *not* to do.

Read each section carefully, then take the quiz. You need **70%** to pass; you have up to **3 attempts**.
$$,
  'COMPLIANCE',
  1.5,
  TRUE,
  70,
  3,
  'PUBLISHED',
  12,
  now(),
  'system'
);

INSERT INTO learning.quiz_question
  (course_id, question_no, question_type, question_text, choices_json, correct_keys, explanation, points)
VALUES
  ('a1111111-1111-1111-1111-111111111111', 1, 'MULTIPLE_CHOICE',
   'Which of the following is the strongest indicator of a phishing email?',
   '[{"key":"A","text":"It is signed by your manager"},
     {"key":"B","text":"It comes from a free email domain and asks you to click a link to reset credentials"},
     {"key":"C","text":"It uses the corporate logo"},
     {"key":"D","text":"It arrives during business hours"}]'::jsonb,
   '["B"]'::jsonb,
   'Free-domain sender + urgent credential-reset link is the classic phishing pattern.',
   1),
  ('a1111111-1111-1111-1111-111111111111', 2, 'TRUE_FALSE',
   'Sharing one shared password across the team is acceptable for low-risk tools.',
   '[{"key":"T","text":"True"},{"key":"F","text":"False"}]'::jsonb,
   '["F"]'::jsonb,
   'Shared credentials are never acceptable — every user must have their own identity.',
   1),
  ('a1111111-1111-1111-1111-111111111111', 3, 'MULTIPLE_CHOICE',
   'Where should an employee report a suspected data breach?',
   '[{"key":"A","text":"Post in a public Slack channel for visibility"},
     {"key":"B","text":"Wait until Monday and discuss with the team lead"},
     {"key":"C","text":"Report immediately to the Security / IT incident channel and lock down affected accounts"},
     {"key":"D","text":"Forward the email to the entire company"}]'::jsonb,
   '["C"]'::jsonb,
   'Speed + containment via the official channel — minutes matter.',
   1),
  ('a1111111-1111-1111-1111-111111111111', 4, 'MULTI_SELECT',
   'Which of the following are characteristics of strong passwords? (select all that apply)',
   '[{"key":"A","text":"At least 12 characters long"},
     {"key":"B","text":"Reused across multiple sites"},
     {"key":"C","text":"Mix of words, numbers, and symbols / a memorable pass-phrase"},
     {"key":"D","text":"Stored in a reputable password manager"}]'::jsonb,
   '["A","C","D"]'::jsonb,
   'Length + uniqueness + storage in a password manager covers the bases.',
   2);

INSERT INTO learning.course_competency (course_id, competency_id, awarded_level) VALUES
    ('a1111111-1111-1111-1111-111111111111', 'e1111111-1111-1111-1111-111111111111', 3),
    ('a1111111-1111-1111-1111-111111111111', 'e2222222-2222-2222-2222-222222222222', 2);
