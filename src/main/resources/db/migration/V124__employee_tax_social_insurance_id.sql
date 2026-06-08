-- M205: Add tax_id (VÖEN) and social_insurance_id (DSMF number) to the
-- employee table.  Both fields are required by PRD §8.1.1 ("Tax identification
-- number" and "Social insurance ID (DSMF)") and are also consumed by the
-- bank-file generator (§8.9.6 / BankFileService) which emits VÖEN in the
-- ABB corporate salary-disbursement template.
--
-- Both columns are stored encrypted at rest (AES-256-GCM via
-- EncryptedStringConverter, identical scheme to national_id).  Column length
-- 500 matches the encrypted-blob size of the longest raw value + IV + tag.

ALTER TABLE core_hr.employee
    ADD COLUMN tax_id             VARCHAR(500),   -- VÖEN (individual taxpayer number)
    ADD COLUMN social_insurance_id VARCHAR(500);  -- DSMF social-insurance number
