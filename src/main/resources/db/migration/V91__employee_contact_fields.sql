-- M133 — Section 3 contact field completion.
--
-- The Employee Management spec §3 lists five work-contact fields the
-- codebase never carried. Personal email + phone exist on the Employee
-- table since V1; emergency contact and multi-address tracking landed
-- in M63 (V51). What was still missing:
--
--   alt_phone       : personal alternative number — distinct from the
--                     emergency contact's alt phone, which belongs to a
--                     different person.
--   work_email      : business address — typically @company.com. Kept
--                     separate from `email` (which the spec treats as
--                     personal) so payslip / letter rendering can pick
--                     the right one.
--   work_phone      : main office line.
--   extension       : internal PBX extension, 1–10 chars.
--   desk_number     : seat / desk identifier — useful for IT asset and
--                     facility teams.
--
-- All five are nullable; existing rows pass through unchanged.

ALTER TABLE core_hr.employee
    ADD COLUMN IF NOT EXISTS alt_phone   VARCHAR(32),
    ADD COLUMN IF NOT EXISTS work_email  VARCHAR(160),
    ADD COLUMN IF NOT EXISTS work_phone  VARCHAR(32),
    ADD COLUMN IF NOT EXISTS extension   VARCHAR(10),
    ADD COLUMN IF NOT EXISTS desk_number VARCHAR(32);

-- A loose email shape check — enough to catch typos like "name@company"
-- (missing TLD) without trying to recreate RFC 5322 in SQL.
ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS chk_employee_work_email;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT chk_employee_work_email
    CHECK (work_email IS NULL
           OR work_email ~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$');

-- Common lookups: "who is at desk 4A?" / "what's the extension for X?"
CREATE INDEX IF NOT EXISTS idx_employee_extension
    ON core_hr.employee (extension)
    WHERE extension IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_employee_desk_number
    ON core_hr.employee (desk_number)
    WHERE desk_number IS NOT NULL;
