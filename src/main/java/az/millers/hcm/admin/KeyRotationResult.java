package az.millers.hcm.admin;

/**
 * Summary returned by {@link KeyRotationService#rotate(boolean)}.
 *
 * @param dryRun   {@code true} when no rows were actually written.
 * @param rotated  Number of column values re-encrypted from enc:v1: → enc:v2:.
 * @param skipped  Column values already on enc:v2: or legacy plaintext — untouched.
 * @param failed   Column values where decryption or re-encryption threw an exception.
 * @param details  Human-readable per-table breakdown.
 */
public record KeyRotationResult(
        boolean dryRun,
        int rotated,
        int skipped,
        int failed,
        String details) {}
