-- M495 — Mobile attendance offline queue deduplication.
--
-- Mobile clients may queue punches offline and re-sync them when they
-- reconnect. This column prevents duplicate insertion when the same
-- offline event is re-transmitted. NULL for real-time punches (most);
-- partial unique index enforces no collision when set.
--
-- NOTE: attendance_event is RANGE-partitioned by event_time (V112), so
-- the unique index must include event_time (the partition key).

ALTER TABLE attendance.attendance_event
    ADD COLUMN IF NOT EXISTS offline_queue_id VARCHAR(120);

COMMENT ON COLUMN attendance.attendance_event.offline_queue_id IS
    'Client-side deduplication key for offline-queued punches (nullable)';

CREATE UNIQUE INDEX IF NOT EXISTS idx_attendance_event_offline_queue
    ON attendance.attendance_event (offline_queue_id, event_time)
    WHERE offline_queue_id IS NOT NULL;
