package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import az.millers.hcm.payroll.domain.ErpExport;
import az.millers.hcm.payroll.domain.ErpExportLine;

/** DTOs for the M158 ERP payroll journal export feature (§17.2). */
public final class ErpExportDtos {

    private ErpExportDtos() {}

    public record ErpGenerateRequest(
            /** CSV_GENERIC | CSV_1C | CSV_DYNAMICS365 | JSON */
            @NotNull String format,
            @NotNull LocalDate postingDate,
            @Size(max = 100) String referenceNo,
            @Size(max = 80) String journalType) {}

    public record ErpLineResponse(
            UUID id,
            int lineNo,
            String accountCode,
            String accountName,
            String costCentre,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            String currency,
            int employeeCount) {

        public static ErpLineResponse from(ErpExportLine l) {
            return new ErpLineResponse(l.getId(), l.getLineNo(), l.getAccountCode(),
                    l.getAccountName(), l.getCostCentre(), l.getDescription(),
                    l.getDebit(), l.getCredit(), l.getCurrency(), l.getEmployeeCount());
        }
    }

    public record ErpExportResponse(
            UUID id,
            String exportNo,
            UUID runId,
            String format,
            String status,
            LocalDate postingDate,
            String referenceNo,
            String journalType,
            int lineCount,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            String errorMessage,
            Long fileSizeBytes,
            String createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime generatedAt,
            List<ErpLineResponse> lines) {

        public static ErpExportResponse from(ErpExport e, boolean includeLines) {
            List<ErpLineResponse> lineList = includeLines
                    ? e.getLines().stream().map(ErpLineResponse::from).toList()
                    : List.of();
            return new ErpExportResponse(
                    e.getId(), e.getExportNo(), e.getRunId(), e.getFormat(),
                    e.getStatus(), e.getPostingDate(), e.getReferenceNo(),
                    e.getJournalType(), e.getLineCount(), e.getTotalDebit(),
                    e.getTotalCredit(), e.getErrorMessage(), e.getFileSizeBytes(),
                    e.getCreatedBy(), e.getCreatedAt(), e.getGeneratedAt(), lineList);
        }
    }
}
