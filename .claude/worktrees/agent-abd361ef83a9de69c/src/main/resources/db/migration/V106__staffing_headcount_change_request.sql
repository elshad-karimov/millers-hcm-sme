-- M156: Headcount change request (§8.3.7 — staffing approval workflow).
--
-- A Department Head submits a headcount-increase request.  The request
-- passes through the configurable workflow engine.  On approval the
-- service increments approved_headcount on the position and opens the
-- corresponding number of VACANT vacancy records.

CREATE TABLE staffing.headcount_change_request (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    position_id         UUID        NOT NULL REFERENCES staffing.position(id) ON DELETE CASCADE,
    -- Positive = increase, negative = decrease (must not drop below occupied).
    requested_delta     INT         NOT NULL CHECK (requested_delta <> 0),
    reason              TEXT,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING'
                                    CHECK (status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN')),
    workflow_instance_id UUID,
    requested_by        VARCHAR(80),
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by         VARCHAR(80),
    approved_at         TIMESTAMPTZ,
    rejected_by         VARCHAR(80),
    rejected_at         TIMESTAMPTZ,
    reject_reason       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_hcr_position ON staffing.headcount_change_request (position_id);
CREATE INDEX idx_hcr_status   ON staffing.headcount_change_request (status)
    WHERE status = 'PENDING';
CREATE INDEX idx_hcr_workflow  ON staffing.headcount_change_request (workflow_instance_id)
    WHERE workflow_instance_id IS NOT NULL;
