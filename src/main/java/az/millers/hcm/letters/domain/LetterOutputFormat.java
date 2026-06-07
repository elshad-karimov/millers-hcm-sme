package az.millers.hcm.letters.domain;

/**
 * Storage / display format for the rendered letter body. The renderer is the
 * same regardless of format — {@code OutputFormat} drives the MIME hint
 * returned on download and the renderer used by the frontend.
 */
public enum LetterOutputFormat {
    TEXT,
    HTML,
    /** M139 — renders via OpenPDF with QR verification + signature line. */
    PDF
}
