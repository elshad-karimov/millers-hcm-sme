-- ----------------------------------------------------------------------------
-- M284 — Recruitment PRD §33: counteroffer + offer revision history.
--
-- Each row snapshots the offer's terms BEFORE a revision is applied,
-- so "previous offer comparison" (PRD §33) is a plain SELECT. The
-- revision reason distinguishes a candidate counteroffer from an
-- HR-side adjustment.
--
-- The re-approval rule (PRD §33 "any material change after approval
-- should trigger re-approval") is enforced in OfferService: revising
-- drops the offer back to DRAFT and the M276 approval workflow runs
-- again, exception-routing included.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS recruitment.offer_revision (
    id                   UUID PRIMARY KEY,
    offer_id             UUID         NOT NULL REFERENCES recruitment.offer(id),
    revision_no          INT          NOT NULL,
    -- snapshot of terms BEFORE the revision
    prev_salary          NUMERIC(12,2),
    prev_currency        VARCHAR(3),
    prev_start_date      DATE,
    prev_benefits        TEXT,
    prev_status          VARCHAR(20)  NOT NULL,
    -- why the terms changed
    reason               VARCHAR(30)  NOT NULL,
    notes                TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(120),
    CONSTRAINT chk_offer_revision_reason CHECK (reason IN
        ('CANDIDATE_COUNTER','HR_REVISION')),
    CONSTRAINT uq_offer_revision UNIQUE (offer_id, revision_no)
);

CREATE INDEX IF NOT EXISTS idx_offer_revision_offer
    ON recruitment.offer_revision(offer_id);
