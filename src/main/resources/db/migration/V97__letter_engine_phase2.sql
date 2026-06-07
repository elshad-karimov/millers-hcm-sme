-- M139 — letter engine Phase 2: multi-language, PDF rendering, QR
-- verification, signature.
--
-- The audit found four §29 gaps on top of the M77 letter engine. This
-- migration lays down the schema; the service layer + renderer ships in
-- the same milestone.
--
--   language column on letter_template — ISO 639-1 alpha-2, same
--     convention as employee.native_language (M132). The render path
--     picks the template variant matching the employee's native_language
--     when available, falling back to 'en'. Templates of the same `code`
--     can now coexist as long as language differs.
--
--   rendered_pdf_url on letter_request — once PDF rendering ships, the
--     URL points at MinIO (same storage as attachments). When NULL the
--     letter has only the text/html body.
--
--   verification_token + verified_at on letter_request — opaque random
--     token printed as a QR code on the rendered PDF; an external verifier
--     hits /api/public/letters/verify/{token} to confirm the letter's
--     authenticity without ever seeing PII.
--
--   signed_by + signed_at on letter_request — captures who signed +
--     when. Rendered into the signature line on the PDF.

-- ── 1. Multi-language templates ────────────────────────────────────────
ALTER TABLE hr_letters.letter_template
    DROP CONSTRAINT IF EXISTS letter_template_code_key;
ALTER TABLE hr_letters.letter_template
    ADD COLUMN IF NOT EXISTS language VARCHAR(2) NOT NULL DEFAULT 'en';

ALTER TABLE hr_letters.letter_template
    DROP CONSTRAINT IF EXISTS chk_letter_template_language;
ALTER TABLE hr_letters.letter_template
    ADD CONSTRAINT chk_letter_template_language
    CHECK (length(language) = 2 AND language = lower(language));

-- New uniqueness: same code can have one row per language.
ALTER TABLE hr_letters.letter_template
    DROP CONSTRAINT IF EXISTS uq_letter_template_code_lang;
ALTER TABLE hr_letters.letter_template
    ADD CONSTRAINT uq_letter_template_code_lang
    UNIQUE (code, language);

-- ── 2. Output format whitelist gains PDF ────────────────────────────────
ALTER TABLE hr_letters.letter_template
    DROP CONSTRAINT IF EXISTS chk_letter_template_format;
ALTER TABLE hr_letters.letter_template
    ADD CONSTRAINT chk_letter_template_format
    CHECK (output_format IN ('TEXT','HTML','PDF'));

-- ── 3. letter_request: PDF URL + verification + signature ──────────────
ALTER TABLE hr_letters.letter_request
    ADD COLUMN IF NOT EXISTS rendered_pdf_url    VARCHAR(500),
    ADD COLUMN IF NOT EXISTS verification_token  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS verified_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS signed_by           VARCHAR(160),
    ADD COLUMN IF NOT EXISTS signed_at           TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS language            VARCHAR(2);

-- Tokens are random 32-char strings; UNIQUE keeps the verify endpoint
-- collision-safe.
ALTER TABLE hr_letters.letter_request
    DROP CONSTRAINT IF EXISTS uq_letter_request_token;
ALTER TABLE hr_letters.letter_request
    ADD CONSTRAINT uq_letter_request_token
    UNIQUE (verification_token);

-- Used by the public verify endpoint — index supports lookup by token.
CREATE INDEX IF NOT EXISTS idx_letter_request_token
    ON hr_letters.letter_request (verification_token)
    WHERE verification_token IS NOT NULL;
