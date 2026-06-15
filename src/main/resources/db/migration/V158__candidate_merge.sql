-- ----------------------------------------------------------------------------
-- M293 — Recruitment PRD §12: candidate duplicate detection + merge.
--
-- A merged candidate is never hard-deleted (its applications / history stay
-- auditable); instead it points at the surviving master via merged_into_id
-- and drops out of active candidate lists + the duplicate scan.
-- ----------------------------------------------------------------------------

ALTER TABLE recruitment.candidate
    ADD COLUMN IF NOT EXISTS merged_into_id UUID
        REFERENCES recruitment.candidate(id);

CREATE INDEX IF NOT EXISTS idx_candidate_merged_into
    ON recruitment.candidate (merged_into_id)
    WHERE merged_into_id IS NOT NULL;
