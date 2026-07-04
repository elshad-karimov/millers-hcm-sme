-- HCM_12 Performance Management — M397 (Phase D.2)
-- Calibration committee (PRD §19.1): named members per calibration session with
-- roles; non-HR calibration edits require CHAIR/MEMBER membership. Calibration
-- notes stay HR/committee-only (§19.3/§31.3 — masked from employees in the API).

CREATE TABLE performance.calibration_committee_member (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   varchar(64) NOT NULL DEFAULT 'default',
    session_id  uuid NOT NULL REFERENCES performance.calibration_session (id) ON DELETE CASCADE,
    employee_id uuid NOT NULL,
    member_role varchar(20) NOT NULL DEFAULT 'MEMBER',
    added_by    varchar(80),
    added_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT calibration_member_unique UNIQUE (session_id, employee_id),
    CONSTRAINT calibration_member_role_check CHECK (member_role IN
        ('CHAIR','MEMBER','OBSERVER','HR_FACILITATOR'))
);
CREATE INDEX calibration_member_session_idx
    ON performance.calibration_committee_member (session_id);

COMMENT ON TABLE performance.calibration_committee_member IS
    'HCM_12 M397 — calibration-session committee (§19.1); OBSERVERs see, CHAIR/MEMBERs edit.';
