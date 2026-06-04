-- M116 — Engagement surveys + eNPS.
--
-- Templates are the reusable blueprint (questions live here).
-- Campaigns instantiate a template against a target audience and a date
-- window. Responses are one row per (campaign, employee) — or per
-- anonymous submission when the campaign is flagged anonymous.

CREATE SCHEMA IF NOT EXISTS engagement;

-- ── Templates ──────────────────────────────────────────────────────────
CREATE TABLE engagement.survey_template (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code         varchar(40)  NOT NULL UNIQUE,
    name         varchar(200) NOT NULL,
    description  text,
    /** When true, responses store NULL employee_id (truly anonymous). */
    anonymous    boolean      NOT NULL DEFAULT false,
    active       boolean      NOT NULL DEFAULT true,
    created_by   varchar(80),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX survey_template_active_idx ON engagement.survey_template(active);

-- ── Questions ──────────────────────────────────────────────────────────
CREATE TABLE engagement.survey_question (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id   uuid NOT NULL REFERENCES engagement.survey_template(id) ON DELETE CASCADE,
    order_index   int  NOT NULL,
    prompt        text NOT NULL,
    /** RATING_1_5 / RATING_1_10 / BOOLEAN / TEXT / MULTIPLE_CHOICE. */
    question_type varchar(20) NOT NULL,
    /** Free-form question metadata (e.g. MULTIPLE_CHOICE options). */
    metadata      jsonb,
    required      boolean NOT NULL DEFAULT true,
    CONSTRAINT survey_question_type_check
        CHECK (question_type IN ('RATING_1_5', 'RATING_1_10',
                                 'BOOLEAN', 'TEXT', 'MULTIPLE_CHOICE')),
    CONSTRAINT survey_question_order_nonneg CHECK (order_index >= 0)
);

CREATE UNIQUE INDEX survey_question_template_order_uk
    ON engagement.survey_question (template_id, order_index);

-- ── Campaigns ──────────────────────────────────────────────────────────
CREATE TABLE engagement.survey_campaign (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id  uuid         NOT NULL REFERENCES engagement.survey_template(id),
    name         varchar(200) NOT NULL,
    description  text,
    status       varchar(20)  NOT NULL DEFAULT 'DRAFT',
    opens_on     date         NOT NULL,
    closes_on    date         NOT NULL,
    /** Audience selection — Phase 1 is "all active employees" only. */
    target_all   boolean      NOT NULL DEFAULT true,
    created_by   varchar(80),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT survey_campaign_status_check
        CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED', 'CANCELLED')),
    CONSTRAINT survey_campaign_date_window
        CHECK (closes_on >= opens_on)
);

CREATE INDEX survey_campaign_status_idx ON engagement.survey_campaign(status);
CREATE INDEX survey_campaign_window_idx ON engagement.survey_campaign(opens_on, closes_on);

-- ── Responses ──────────────────────────────────────────────────────────
CREATE TABLE engagement.survey_response (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id   uuid         NOT NULL REFERENCES engagement.survey_campaign(id),
    /** NULL on anonymous templates. */
    employee_id   uuid,
    submitted_at  timestamptz  NOT NULL DEFAULT now(),
    /** Optional free-text comment / general feedback. */
    comment       text
);

-- At most one identified response per (campaign, employee) — anonymous
-- responses skip the constraint via partial index.
CREATE UNIQUE INDEX survey_response_employee_uk
    ON engagement.survey_response (campaign_id, employee_id)
    WHERE employee_id IS NOT NULL;

CREATE INDEX survey_response_campaign_idx ON engagement.survey_response(campaign_id);

-- ── Answers ────────────────────────────────────────────────────────────
CREATE TABLE engagement.survey_answer (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    response_id   uuid NOT NULL REFERENCES engagement.survey_response(id) ON DELETE CASCADE,
    question_id   uuid NOT NULL REFERENCES engagement.survey_question(id),
    /** Populated for RATING_1_5 (1..5), RATING_1_10 (0..10), BOOLEAN (0/1). */
    rating_value  int,
    /** Populated for TEXT. */
    text_value    text,
    /** Populated for MULTIPLE_CHOICE — the chosen option string. */
    choice_value  varchar(200)
);

CREATE UNIQUE INDEX survey_answer_response_question_uk
    ON engagement.survey_answer (response_id, question_id);

CREATE INDEX survey_answer_question_idx ON engagement.survey_answer(question_id);

COMMENT ON TABLE engagement.survey_template IS
    'M116 — Reusable survey blueprint. Questions live in survey_question.';
COMMENT ON TABLE engagement.survey_campaign IS
    'M116 — A run of a template against an audience for a date window.';
COMMENT ON TABLE engagement.survey_response IS
    'M116 — One row per (campaign, employee). employee_id NULL = anonymous.';
COMMENT ON TABLE engagement.survey_answer IS
    'M116 — One row per (response, question). Only one of '
    'rating_value / text_value / choice_value is populated, matching the question type.';
