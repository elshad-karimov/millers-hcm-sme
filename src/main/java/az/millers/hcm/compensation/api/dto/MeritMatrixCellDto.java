package az.millers.hcm.compensation.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import az.millers.hcm.compensation.domain.MeritMatrixCell;

public record MeritMatrixCellDto(
        UUID id,
        String performanceBand,
        String rangePosition,
        BigDecimal meritPct
) {
    public static MeritMatrixCellDto from(MeritMatrixCell c) {
        return new MeritMatrixCellDto(
                c.getId(),
                c.getPerformanceBand(),
                c.getRangePosition(),
                c.getMeritPct()
        );
    }

    public static MeritMatrixCell toEntity(MeritMatrixCellDto dto) {
        MeritMatrixCell c = new MeritMatrixCell();
        c.setPerformanceBand(dto.performanceBand());
        c.setRangePosition(dto.rangePosition());
        c.setMeritPct(dto.meritPct());
        return c;
    }
}
