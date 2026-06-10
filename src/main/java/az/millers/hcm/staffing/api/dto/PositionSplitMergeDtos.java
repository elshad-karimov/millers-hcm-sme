package az.millers.hcm.staffing.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** M273 — PRD §41 Split & Merge DTOs. */
public final class PositionSplitMergeDtos {

    private PositionSplitMergeDtos() {}

    // ── Split ──────────────────────────────────────────────────────

    public record SplitRequest(
            @NotEmpty List<SplitTarget> targets,
            @Size(max = 500) String reason) {}

    public record SplitTarget(
            @NotBlank @Size(max = 200) String title,
            @Min(0) int approvedHeadcount,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            @Size(max = 64)  String grade,
            @Size(max = 64)  String jobFamily,
            @Size(max = 32)  String jobLevel) {}

    public record SplitResult(
            UUID sourcePositionId,
            String sourceCode,
            List<NewPositionRow> created) {}

    public record NewPositionRow(UUID positionId, String code, String title, int approvedHeadcount) {}

    // ── Merge ──────────────────────────────────────────────────────

    public record MergeRequest(
            @NotEmpty List<UUID> sourcePositionIds,
            /** Existing destination position id, OR null to create a new one. */
            UUID destinationPositionId,
            /** Required when destinationPositionId is null. */
            NewDestination newDestination,
            @Size(max = 500) String reason) {}

    public record NewDestination(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 200) String orgUnitLabel,
            UUID orgUnitId,
            @Size(max = 32) String grade,
            @Size(max = 64) String jobFamily,
            @Size(max = 32) String jobLevel) {}

    public record MergeResult(
            UUID destinationPositionId,
            String destinationCode,
            int totalHeadcount,
            List<UUID> archivedSourceIds) {}
}
