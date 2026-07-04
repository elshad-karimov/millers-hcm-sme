-- M148: Branch/Store enrichment fields on org_unit (§28).
--
-- Nullable on all units; expected to be set only for BRANCH / STORE types.
-- operating_hours is free text (e.g. "Mon-Fri 09:00-18:00, Sat 10:00-16:00").
-- GPS pair stored as NUMERIC(9,6) to preserve full decimal precision.
-- attendance_device_id links to the physical door/biometric reader.
-- pos_system_ref holds the POS terminal or system reference.

ALTER TABLE organization.org_unit
    ADD COLUMN IF NOT EXISTS gps_lat              NUMERIC(9, 6),
    ADD COLUMN IF NOT EXISTS gps_lng              NUMERIC(9, 6),
    ADD COLUMN IF NOT EXISTS operating_hours      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS attendance_device_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS pos_system_ref       VARCHAR(120);
