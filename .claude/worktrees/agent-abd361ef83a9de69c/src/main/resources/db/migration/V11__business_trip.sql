-- Business Trip module (PRD Section 8.6).
-- Trip request with advance amount tracking (requested / approved / paid /
-- actual). Auto-timesheet integration with the BT code lands when the
-- Timesheet module ships (PRD 8.8); MinIO attachment uploads land with the
-- document-storage module.

CREATE SEQUENCE business_trip.trip_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE business_trip.business_trip_request (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_no                  VARCHAR(32)  NOT NULL UNIQUE,

    employee_id              UUID         NOT NULL,

    -- DOMESTIC / INTERNATIONAL  (PRD 8.6.1)
    trip_type                VARCHAR(32)  NOT NULL,
    destination_country      VARCHAR(80),
    destination_city         VARCHAR(120) NOT NULL,
    purpose                  TEXT,
    project                  VARCHAR(120),
    cost_centre              VARCHAR(64),

    start_date               DATE         NOT NULL,
    end_date                 DATE         NOT NULL,
    total_days               INTEGER      NOT NULL,

    -- Advance reconciliation (PRD 8.6.5)
    currency                 VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    daily_allowance          NUMERIC(12,2),
    requested_advance        NUMERIC(12,2),
    approved_advance         NUMERIC(12,2),
    paid_advance             NUMERIC(12,2) NOT NULL DEFAULT 0,
    actual_expense           NUMERIC(12,2),

    meals_provided           BOOLEAN      NOT NULL DEFAULT FALSE,
    accommodation_provided   BOOLEAN      NOT NULL DEFAULT FALSE,
    attachment_urls          TEXT,         -- newline-separated until MinIO uploads ship

    -- DRAFT, PENDING, APPROVED, REJECTED, CANCELLED, COMPLETED
    status                   VARCHAR(32)  NOT NULL,
    workflow_instance_id     UUID,

    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               VARCHAR(120),

    CONSTRAINT chk_trip_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_trip_days  CHECK (total_days > 0),
    CONSTRAINT chk_trip_type  CHECK (trip_type IN ('DOMESTIC', 'INTERNATIONAL'))
);

CREATE INDEX idx_trip_employee ON business_trip.business_trip_request (employee_id, start_date);
CREATE INDEX idx_trip_status   ON business_trip.business_trip_request (status);
