package az.millers.hcm.corehr.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EmployeeImportJob;
import az.millers.hcm.corehr.domain.ImportJobStatus;

public record ImportJobResponse(
        UUID id,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String startedBy,
        String fileName,
        Long fileSizeBytes,
        ImportJobStatus status,
        boolean dryRun,
        int rowsTotal,
        int rowsValid,
        int rowsInvalid,
        int rowsCommitted,
        Object errorReport,
        String errorMessage) {

    public static ImportJobResponse from(EmployeeImportJob j) {
        return new ImportJobResponse(
                j.getId(),
                j.getStartedAt(),
                j.getFinishedAt(),
                j.getStartedBy(),
                j.getFileName(),
                j.getFileSizeBytes(),
                j.getStatus(),
                j.isDryRun(),
                j.getRowsTotal(),
                j.getRowsValid(),
                j.getRowsInvalid(),
                j.getRowsCommitted(),
                j.getErrorReport(),
                j.getErrorMessage());
    }
}
