-- HCM_12 Performance Management — M396 (Phase D.1)
-- Result acknowledgement (PRD §25) + appeals (§26). The employee acknowledges the
-- final result (optionally disputing it); a dispute can become a formal appeal:
-- SUBMITTED → UNDER_REVIEW → APPROVED / REJECTED / RETURNED → CLOSED.
-- §37.12: an APPROVED adjustment NEVER overwrites the original rating — it goes
-- through the M394 override path, which preserves original_rating + audits.

ALTER TABLE performance.performance_review
    ADD COLUMN acknowledged_at         timestamptz,
    ADD COLUMN acknowledged_comments   varchar(2000),
    ADD COLUMN acknowledgement_disputed boolean NOT NULL DEFAULT false;

CREATE TABLE performance.performance_appeal (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       varchar(64) NOT NULL DEFAULT 'default',
    review_id       uuid NOT NULL REFERENCES performance.performance_review (id),
    employee_id     uuid NOT NULL,
    reason          varchar(2000) NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'SUBMITTED',
    original_rating numeric(6,3),
    adjusted_rating numeric(6,3),
    decision_notes  varchar(2000),
    submitted_by    varchar(80),
    submitted_at    timestamptz NOT NULL DEFAULT now(),
    decided_by      varchar(80),
    decided_at      timestamptz,
    closed_at       timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT performance_appeal_status_check CHECK (status IN
        ('SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED','RETURNED','CLOSED'))
);
CREATE INDEX performance_appeal_review_idx ON performance.performance_appeal (review_id);
CREATE INDEX performance_appeal_status_idx ON performance.performance_appeal (tenant_id, status);

COMMENT ON TABLE performance.performance_appeal IS
    'HCM_12 M396 — appeal against a review result (§26); approved adjustments preserve the original rating (§37.12).';
