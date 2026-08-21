-- Split the single free-text place of birth into country / city / address.
--
-- "Place of birth" was one varchar holding whatever the user typed, which
-- cannot be filtered, grouped or checked. It becomes three:
--   birth_country  ISO 3166-1 alpha-2, picked from a list (matches
--                  organization.legal_entity.country, which already holds 'AZ')
--   birth_city     picked from a suggested list, free text still accepted
--   birth_address  the remainder — village, district, street
--
-- place_of_birth is NOT dropped. Its content is copied into birth_address
-- first, so nothing typed so far is lost, and the column is left behind
-- deliberately: dropping it is irreversible under a forward-only migration
-- history, and it costs nothing to retire it in a later release once the new
-- columns are known to be carrying the data.

ALTER TABLE core_hr.employee
    ADD COLUMN IF NOT EXISTS birth_country VARCHAR(2),
    ADD COLUMN IF NOT EXISTS birth_city    VARCHAR(120),
    ADD COLUMN IF NOT EXISTS birth_address VARCHAR(255);

-- Carry the existing value across. It is free text, so it cannot be split into
-- country and city reliably; it lands in the address part, which is the field
-- that can hold anything, and HR can refine it from there.
UPDATE core_hr.employee
   SET birth_address = place_of_birth
 WHERE place_of_birth IS NOT NULL
   AND place_of_birth <> ''
   AND birth_address IS NULL;

COMMENT ON COLUMN core_hr.employee.place_of_birth IS
    'DEPRECATED — superseded by birth_country / birth_city / birth_address in V329. '
    'Retained only so the pre-split value stays recoverable; nothing reads it.';
