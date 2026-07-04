package az.millers.hcm.organization.api.dto;

import java.util.List;

import az.millers.hcm.organization.api.dto.BulkReorgManifest.BulkReorgOperation.OperationKind;

/**
 * Per-operation outcome for {@link BulkReorgManifest} (M84).
 *
 * <p>When {@code dryRun=true}, every row carries {@code applied=false}.
 * On a wet run, the service rolls back the whole transaction at the first
 * validation or apply failure — the response reflects whatever the
 * exception handler returns, not a partially-mutated state.
 */
public record BulkReorgResult(
        boolean dryRun,
        int operationsTotal,
        int operationsApplied,
        List<RowResult> rows) {

    public record RowResult(
            int index,
            OperationKind kind,
            String code,
            boolean applied,
            String message) {}
}
