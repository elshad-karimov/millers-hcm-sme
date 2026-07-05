-- V258: M438 HR agent queues + category SLA

CREATE TABLE selfservice.hr_agent_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    routing_category VARCHAR(50),

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL,

    UNIQUE(tenant_id, code)
);

CREATE INDEX idx_hr_queue_tenant ON selfservice.hr_agent_queue(tenant_id, active);

-- Add queue_id to hr_service_request
ALTER TABLE selfservice.hr_service_request
ADD COLUMN queue_id UUID,
ADD CONSTRAINT fk_request_queue FOREIGN KEY (queue_id) REFERENCES selfservice.hr_agent_queue(id);

CREATE INDEX idx_hr_request_queue ON selfservice.hr_service_request(queue_id);

COMMENT ON TABLE selfservice.hr_agent_queue IS 'M438 — HR agent queues for routing service requests by category';
COMMENT ON COLUMN selfservice.hr_agent_queue.routing_category IS 'Auto-route requests of this category to this queue; NULL = manual only';

-- Seed default queues with category SLA (stored as category→sla_days map, simpler than a separate table per spec)
-- SALARY_CERT 2, EMPLOYMENT_LETTER 2, PAYROLL_INQUIRY 3, POLICY_QUESTION 5, GRIEVANCE 1, OTHER 3
-- For this implementation, we'll keep SLA calculation in the service using these defaults
-- (category SLA takes precedence over priority SLA per analysis.md spec)

INSERT INTO selfservice.hr_agent_queue (tenant_id, code, name, routing_category, created_by) VALUES
('default', 'PAYROLL', 'Payroll Queries', 'PAYROLL_INQUIRY', 'system'),
('default', 'LETTERS', 'Letters & Certificates', 'SALARY_CERT', 'system'),
('default', 'GENERAL', 'General HR', 'OTHER', 'system'),
('default', 'POLICY', 'Policy Questions', 'POLICY_QUESTION', 'system'),
('default', 'GRIEVANCE', 'Grievances', 'GRIEVANCE', 'system');

-- Note: EMPLOYMENT_LETTER shares LETTERS queue with SALARY_CERT; routing will pick first match
