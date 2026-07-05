-- V261: M441 expiry→renewal trigger support

-- Add source_ref to hr_service_request for linking auto-created document renewal requests
ALTER TABLE selfservice.hr_service_request
ADD COLUMN source_ref UUID;

CREATE INDEX idx_hr_request_source_ref ON selfservice.hr_service_request(source_ref);

COMMENT ON COLUMN selfservice.hr_service_request.source_ref IS 'M441 — Reference to source entity (e.g., employee_document.id for DOCUMENT_RENEWAL requests)';

-- Add DOCUMENT_RENEWAL to ServiceRequestCategory enum
-- (No migration needed since it's VARCHAR, just adding to the app enum)
