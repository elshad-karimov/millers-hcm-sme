package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** DTOs for provider-file reconciliation (HCM_11 M384). */
public final class BenefitReconcileDtos {

    private BenefitReconcileDtos() {}

    /** One row parsed from the provider's roster file. */
    public record FileRow(String reference, BigDecimal amount) {}

    public record ReconcileRequest(
            @NotNull UUID providerId,
            @NotNull List<FileRow> rows) {
    }

    /** A reconciliation discrepancy / match line. */
    public record ReconcileLine(
            String reference,
            String employeeName,
            BigDecimal systemAmount,
            BigDecimal fileAmount,
            String result) {  // MATCHED | AMOUNT_MISMATCH | MISSING_IN_FILE | EXTRA_IN_FILE
    }

    public record ReconcileResponse(
            String providerName,
            int systemMembers,
            int fileMembers,
            int matched,
            int amountMismatch,
            int missingInFile,
            int extraInFile,
            BigDecimal systemTotal,
            BigDecimal fileTotal,
            List<ReconcileLine> lines) {
    }
}
