-- Widen PII columns so they can hold AES-256-GCM ciphertext (PRD 14.3).
-- Wire format is `enc:v1:<base64(IV(12) || ciphertext || tag(16))>` — for a
-- 16-char plaintext that's ~70 chars; for a 40-char IBAN it's ~120. We
-- bump to 500 to be future-proof across the various PII field lengths.
--
-- The JPA layer reads ciphertext back transparently via
-- {@code EncryptedStringConverter}. Existing plaintext rows continue to
-- read fine (the converter detects the `enc:v1:` marker and falls
-- through for legacy values) and will be re-encrypted the next time the
-- application updates them.

ALTER TABLE core_hr.employee     ALTER COLUMN national_id     TYPE VARCHAR(500);
ALTER TABLE payroll.bank_account ALTER COLUMN iban            TYPE VARCHAR(500);
ALTER TABLE payroll.bank_account ALTER COLUMN account_number  TYPE VARCHAR(500);
