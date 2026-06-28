-- M341: Partial/hourly leave support
-- leave_type gains a unit discriminator and hours-per-day for conversion
-- leave_request gains time fields for intra-day requests

ALTER TABLE leave_mgmt.leave_type
    ADD COLUMN leave_unit    VARCHAR(10) NOT NULL DEFAULT 'DAYS',
    ADD COLUMN hours_per_day NUMERIC(4, 2) DEFAULT 8.0;

ALTER TABLE leave_mgmt.leave_request
    ADD COLUMN start_time     TIME,
    ADD COLUMN end_time       TIME,
    ADD COLUMN duration_hours NUMERIC(5, 2);

-- Ensure consistent default for existing rows (already defaulted by NOT NULL DEFAULT above,
-- but explicit for clarity in audit).
COMMENT ON COLUMN leave_mgmt.leave_type.leave_unit IS
    'DAYS = whole/half-day requests; HALF_DAY = type forces half-day only; HOURS = time-based requests';
COMMENT ON COLUMN leave_mgmt.leave_type.hours_per_day IS
    'Working hours per day used to convert durationHours → fractional leave days (default 8)';
COMMENT ON COLUMN leave_mgmt.leave_request.duration_hours IS
    'For HOURS-unit leave types: the gross request duration in hours (endTime - startTime)';
