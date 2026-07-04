-- M56: Calibration Session layer (PRD §8.13 calibration meeting tooling)
-- Stores structured facilitated meetings that group performance_review records
-- for a cycle and track the session lifecycle.

CREATE TABLE performance.calibration_session (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    cycle_id        UUID         NOT NULL
                        REFERENCES performance.review_cycle(id) ON DELETE CASCADE,
    name            VARCHAR(160) NOT NULL,
    scheduled_at    TIMESTAMPTZ,
    status          VARCHAR(24)  NOT NULL DEFAULT 'SCHEDULED',
    facilitator     VARCHAR(120),
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(120),

    CONSTRAINT pk_calibration_session PRIMARY KEY (id),
    CONSTRAINT chk_calibration_session_status CHECK (
        status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED')
    )
);

CREATE INDEX idx_calibration_session_cycle_id
    ON performance.calibration_session (cycle_id);
