package az.millers.hcm.compensation.api.dto;

import java.util.List;
import java.util.UUID;

import az.millers.hcm.compensation.domain.MeritMatrix;
import az.millers.hcm.compensation.domain.MeritMatrixCell;

public record MeritMatrixDto(
        UUID id,
        String code,
        String name,
        Boolean isActive,
        List<MeritMatrixCellDto> cells
) {
    public static MeritMatrixDto from(MeritMatrix m) {
        return new MeritMatrixDto(
                m.getId(),
                m.getCode(),
                m.getName(),
                m.getIsActive(),
                null
        );
    }

    public static MeritMatrixDto fromWithCells(MeritMatrix m, List<MeritMatrixCell> cells) {
        return new MeritMatrixDto(
                m.getId(),
                m.getCode(),
                m.getName(),
                m.getIsActive(),
                cells.stream().map(MeritMatrixCellDto::from).toList()
        );
    }
}
