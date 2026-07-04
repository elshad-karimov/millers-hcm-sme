-- ----------------------------------------------------------------------------
-- M248 — Position profile (Phase F of Position Management spec).
--
-- A unified definition of "what each position requires" — a single
-- table that captures every type of thing a position auto-implies for
-- its occupants:
--
--   ALLOWANCE         (PRD §28) — vehicle, phone, housing, meal, hazard...
--   REQUIRED_DOCUMENT (PRD §29) — driver license, medical cert, training cert
--   TRAINING          (PRD §30) — mandatory courses to complete on hire
--   EQUIPMENT         (PRD §25) — laptop, uniform, POS terminal, vehicle
--   ACCESS_ROLE       (PRD §26) — ERP/HCM/POS access bundle granted
--   CHECKLIST_ITEM    (PRD §25) — free-form onboarding to-do
--   APPROVAL_LIMIT    (PRD §27) — PO/expense/leave approval threshold
--
-- Phase F delivers the *definition* layer + visibility on the SPA.
-- Phase F.2 will wire individual item types into their downstream
-- modules (allowance.create, learning.enrol, attachment.required-list,
-- etc.) so the auto-grant happens on occupancy.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS staffing.position_profile_item (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    position_id  UUID NOT NULL REFERENCES staffing.position(id) ON DELETE CASCADE,
    item_type    VARCHAR(32) NOT NULL,
    label        VARCHAR(200) NOT NULL,
    -- Numeric value for ALLOWANCE / APPROVAL_LIMIT; null for other types.
    -- Stored as NUMERIC so we can show as money.
    value_amount NUMERIC(14,2),
    currency     VARCHAR(3),
    -- True if this item is mandatory for every occupant; false if it's
    -- merely available / suggested.
    mandatory    BOOLEAN NOT NULL DEFAULT true,
    -- Reference key for cross-module linkage (e.g. an allowance type code,
    -- a training course code, an access role name). Free-text now so we
    -- don't force a hard FK before Phase F.2 wires the integrations.
    reference_code VARCHAR(120),
    -- Human-readable detail.
    notes        TEXT,
    -- Display ordering within the position's profile.
    sort_order   INT NOT NULL DEFAULT 0,
    -- Audit
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_position_profile_position
    ON staffing.position_profile_item(position_id, item_type, sort_order);
CREATE INDEX IF NOT EXISTS idx_position_profile_type
    ON staffing.position_profile_item(item_type);
