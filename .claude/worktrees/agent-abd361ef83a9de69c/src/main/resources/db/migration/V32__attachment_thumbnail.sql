-- Server-side thumbnail generation (PRD 14.6 / 16.4 — milestone 36).
--
-- The AttachmentUploader's 48×48 inline image previews (M32) fetch the
-- full upload to render a thumbnail. That meant a 4 MB photo went over
-- the wire just to render 48 pixels — wasteful on bandwidth and bad
-- on slow connections. M36 pre-bakes a 256-px JPEG sibling object on
-- upload (image content types only) so the Drawer fetches ~10 KB.
--
-- Column is nullable because:
--   1. Non-image uploads have no thumbnail.
--   2. Existing pre-M36 rows have no thumbnail until the operator runs
--      a backfill job (not shipped in this milestone — the /thumbnail
--      endpoint falls back to the original blob, so previews keep
--      working without rebuild).
--   3. Thumbnail generation failures (decode error on a corrupt JPG,
--      OOM on a huge TIFF) shouldn't fail the upload — the original
--      still uploads, thumb_object_key stays null, fallback path covers it.

ALTER TABLE core_hr.attachment
    ADD COLUMN thumb_object_key VARCHAR(500);

COMMENT ON COLUMN core_hr.attachment.thumb_object_key IS
    '256-px JPEG thumbnail object key in the same MinIO bucket. NULL for non-image uploads, pre-M36 rows, or rows whose thumbnail generation failed.';
