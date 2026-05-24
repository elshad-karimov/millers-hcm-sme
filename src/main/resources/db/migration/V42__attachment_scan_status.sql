-- M50: PRD 14.8 — virus-scan status tracking on every attachment row.
--
-- scan_status values (mirrors ScanStatus.java enum):
--   PENDING   — uploaded but not yet scanned (transient; should not remain)
--   CLEAN     — ClamAV confirmed no threat
--   INFECTED  — ClamAV flagged as malware (row only exists in theory; upload
--               is rejected before the row is saved — kept for manual overrides)
--   SKIPPED   — scan intentionally skipped (server-generated exports, or scan
--               disabled via HCM_VIRUS_SCAN_ENABLED=false)
--   ERROR     — scanner was reachable but returned an unexpected response

ALTER TABLE core_hr.attachment
    ADD COLUMN IF NOT EXISTS scan_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS scan_at     TIMESTAMPTZ;

-- Back-fill all pre-M50 rows as SKIPPED — they were uploaded before scanning
-- existed and the scanner never ran over them.
UPDATE core_hr.attachment
   SET scan_status = 'SKIPPED'
 WHERE scan_status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_attachment_scan_status
    ON core_hr.attachment (scan_status)
 WHERE deleted = false;
