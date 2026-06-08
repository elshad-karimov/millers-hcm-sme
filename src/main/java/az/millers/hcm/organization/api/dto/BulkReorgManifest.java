package az.millers.hcm.organization.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Bulk-reorg input (M84). Each operation is keyed by the unit's
 * <em>code</em> rather than its UUID so a single manifest can reference
 * units that don't exist yet — they're created earlier in the same batch.
 *
 * <p>Ordering matters: the service applies operations sequentially. ADDs
 * for parents must come before ADDs for children. The validator pre-flights
 * the whole sequence so a half-applied state is impossible.
 *
 * <p>{@code dryRun} returns the planned outcome without writing.
 */
public record BulkReorgManifest(
        boolean dryRun,
        @NotNull @NotEmpty @Size(max = 500)
        List<BulkReorgOperation> operations) {

    /** One reorg step. {@code kind} drives which other fields apply. */
    public record BulkReorgOperation(
            @NotNull OperationKind kind,

            /** Target code — required for every kind. */
            String code,

            // ── ADD-only / UPDATE-friendly fields ────────────────────────
            String name,
            String unitType,
            String parentCode,
            UUID headEmployeeId,
            Integer sortOrder,
            String costCentreCode,
            String location,
            String contactEmail,
            String glAccount,
            Integer headcountBudget,
            Boolean active,

            // ── MOVE-only ─────────────────────────────────────────────────
            String newParentCode,

            // Audit trail
            String reason) {

        public enum OperationKind {
            ADD, UPDATE, MOVE, REMOVE
        }
    }
}
