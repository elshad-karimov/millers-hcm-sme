-- V115 — Workflow revision loop: resubmit support (M174 / PRD §9.1)
--
-- Adds resubmit_count to track how many times a RETURNED instance has been
-- corrected and re-submitted by its initiator. RESUBMIT resets the instance
-- to PENDING at step 1 so the full approval chain runs again from scratch.

ALTER TABLE workflow.workflow_instance
    ADD COLUMN IF NOT EXISTS resubmit_count INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN workflow.workflow_instance.resubmit_count IS
    'M174 — Counts how many times this instance has been returned and re-submitted
     by its initiator. Useful for audit trail and loop-detection (no hard cap at
     DB level; a soft advisory limit is enforced in the service layer).';
