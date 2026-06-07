package az.millers.hcm.corehr.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.api.dto.EmployeeRequest;
import az.millers.hcm.corehr.api.dto.ImportJobResponse;
import az.millers.hcm.corehr.domain.EmployeeImportJob;
import az.millers.hcm.corehr.domain.EmploymentType;
import az.millers.hcm.corehr.domain.MaritalStatus;
import az.millers.hcm.corehr.repo.EmployeeImportJobRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Bulk employee import (M69 / P1-16).
 *
 * <p>Single entry point: {@link #importFile(MultipartFile, boolean)}.
 * Parses the uploaded {@code .xlsx} / {@code .xls}, validates every row,
 * and either:
 * <ul>
 *   <li>commits valid rows to the DB via {@code EmployeeService.create}
 *       (when {@code dryRun=false}), or</li>
 *   <li>returns a validation-only preview (when {@code dryRun=true}).</li>
 * </ul>
 *
 * <p>Both paths persist an {@link EmployeeImportJob} for audit. Invalid rows
 * never block the entire job — they're collected into the error report and
 * the valid rows still get committed (HR can re-upload the corrected ones).
 *
 * <p>Required columns: {@code firstName}, {@code lastName}, {@code hireDate}.
 * Optional columns: middleName, gender, maritalStatus, nationality, nationalId,
 * email, phone, employmentType, ftePercent, departmentName, positionTitle,
 * costCentre, birthDate. Column header lookup is case-insensitive and tolerant
 * of leading / trailing whitespace.
 */
@Service
public class EmployeeImportService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeImportService.class);

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeImportJob";

    private static final List<String> REQUIRED_HEADERS =
            List.of("firstName", "lastName", "hireDate");

    private static final List<String> ALL_HEADERS = List.of(
            "firstName", "lastName", "middleName", "birthDate", "gender",
            "maritalStatus", "nationality", "nationalId", "email", "phone",
            "hireDate", "departmentName", "positionTitle", "costCentre",
            "employmentType", "ftePercent");

    private final EmployeeImportJobRepository jobs;
    private final EmployeeService employeeService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public EmployeeImportService(EmployeeImportJobRepository jobs,
                                  EmployeeService employeeService,
                                  AuditService audit,
                                  CurrentRequest currentRequest) {
        this.jobs = jobs;
        this.employeeService = employeeService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    /** Per-row error captured in the import job's error report JSON column. */
    public record RowError(int row, String message, List<String> fields) {}

    /**
     * Process the upload end-to-end. The persisted {@link EmployeeImportJob}
     * row is the response — callers see total / valid / invalid / committed
     * counts and the full error report.
     *
     * <p>Method is intentionally NOT {@code @Transactional} at this level —
     * we want the job row to land even if individual employee inserts fail.
     * Each successful {@code EmployeeService.create} has its own tx via the
     * service annotation.
     */
    public ImportJobResponse importFile(MultipartFile file, boolean dryRun) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Upload is empty");
        }

        EmployeeImportJob job = new EmployeeImportJob();
        job.setStartedBy(currentRequest.username());
        job.setFileName(file.getOriginalFilename() == null ? "upload.xlsx" : file.getOriginalFilename());
        job.setFileSizeBytes(file.getSize());
        job.setDryRun(dryRun);
        job = jobs.save(job);
        audit.record(MODULE, ENTITY, job.getId().toString(), "IMPORT_STARTED",
                null, java.util.Map.of("file", job.getFileName(), "dryRun", dryRun));

        try {
            List<EmployeeRequest> parsed = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            parseWorkbook(file, parsed, errors);

            job.setRowsTotal(parsed.size() + errors.size());
            job.setRowsValid(parsed.size());
            job.setRowsInvalid(errors.size());

            if (dryRun) {
                job.setStatus(az.millers.hcm.corehr.domain.ImportJobStatus.PREVIEW);
            } else {
                int committed = 0;
                for (int i = 0; i < parsed.size(); i++) {
                    EmployeeRequest req = parsed.get(i);
                    try {
                        employeeService.create(req);
                        committed++;
                    } catch (RuntimeException ex) {
                        // Pull the row from the parsed set into the error report.
                        // Row index is 1-based + header offset to match the spreadsheet.
                        errors.add(new RowError(i + 2,
                                "Create failed: " + ex.getMessage(),
                                List.of()));
                    }
                }
                job.setRowsCommitted(committed);
                job.setRowsValid(committed);
                job.setRowsInvalid(job.getRowsTotal() - committed);
                job.setStatus(az.millers.hcm.corehr.domain.ImportJobStatus.COMMITTED);
            }
            job.setErrorReport(errors);
        } catch (RuntimeException ex) {
            log.warn("Employee import {} failed: {}", job.getId(), ex.toString());
            job.setStatus(az.millers.hcm.corehr.domain.ImportJobStatus.FAILED);
            job.setErrorMessage(ex.toString());
        } finally {
            job.setFinishedAt(OffsetDateTime.now());
            job = jobs.save(job);
            audit.record(MODULE, ENTITY, job.getId().toString(),
                    "IMPORT_FINISHED", null,
                    Map.of(
                            "status", job.getStatus().name(),
                            "rowsTotal", job.getRowsTotal(),
                            "rowsValid", job.getRowsValid(),
                            "rowsCommitted", job.getRowsCommitted(),
                            "rowsInvalid", job.getRowsInvalid()));
        }
        return ImportJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ImportJobResponse> history(
            org.springframework.data.domain.Pageable pageable) {
        return jobs.findAllByOrderByStartedAtDesc(pageable).map(ImportJobResponse::from);
    }

    // ── Workbook parsing ──────────────────────────────────────────────────────

    private void parseWorkbook(MultipartFile file,
                                List<EmployeeRequest> outValidRows,
                                List<RowError> outErrors) {
        try (InputStream in = file.getInputStream();
             Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) throw new BadRequestException("Workbook has no sheets");

            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) throw new BadRequestException("Workbook is empty");
            Map<String, Integer> headerIndex = readHeader(header);

            for (String required : REQUIRED_HEADERS) {
                if (!headerIndex.containsKey(required)) {
                    throw new BadRequestException(
                            "Missing required column: " + required + ". "
                            + "Required columns are " + REQUIRED_HEADERS + ".");
                }
            }

            int last = sheet.getLastRowNum();
            for (int r = sheet.getFirstRowNum() + 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlank(row)) continue;
                try {
                    EmployeeRequest req = readRow(row, headerIndex, r);
                    outValidRows.add(req);
                } catch (RowParseException ex) {
                    outErrors.add(new RowError(r + 1, ex.getMessage(), ex.getFields()));
                } catch (RuntimeException ex) {
                    outErrors.add(new RowError(r + 1, "Parse error: " + ex.getMessage(),
                            List.of()));
                }
            }
        } catch (IOException ex) {
            throw new BadRequestException("Failed to read workbook: " + ex.getMessage());
        }
    }

    private Map<String, Integer> readHeader(Row header) {
        Map<String, Integer> idx = new HashMap<>();
        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            if (cell == null) continue;
            String key = stringValue(cell);
            if (key == null || key.isBlank()) continue;
            String canonical = canonicalKey(key);
            if (canonical != null) idx.put(canonical, c);
        }
        return idx;
    }

    /**
     * Map header text to one of {@link #ALL_HEADERS}. Case-insensitive, ignores
     * surrounding whitespace. Returns {@code null} for unrecognised headers so
     * the import tolerates extra "Notes" / "Comments" columns gracefully.
     */
    private String canonicalKey(String raw) {
        String trimmed = raw.trim();
        for (String h : ALL_HEADERS) {
            if (h.equalsIgnoreCase(trimmed)) return h;
        }
        return null;
    }

    private EmployeeRequest readRow(Row row, Map<String, Integer> idx, int rowIndex0Based) {
        List<String> missing = new ArrayList<>();

        String firstName = trimmedString(row, idx.get("firstName"));
        String lastName = trimmedString(row, idx.get("lastName"));
        LocalDate hireDate = dateValue(row, idx.get("hireDate"));

        if (firstName == null) missing.add("firstName");
        if (lastName == null)  missing.add("lastName");
        if (hireDate == null)  missing.add("hireDate");
        if (!missing.isEmpty()) {
            throw new RowParseException("Missing required field(s): " + String.join(", ", missing),
                    missing);
        }

        EmploymentType empType = enumValue(row, idx.get("employmentType"), EmploymentType.class);
        MaritalStatus mStatus = enumValue(row, idx.get("maritalStatus"), MaritalStatus.class);

        return new EmployeeRequest(
                firstName,
                lastName,
                trimmedString(row, idx.get("middleName")),
                dateValue(row, idx.get("birthDate")),
                trimmedString(row, idx.get("gender")),
                mStatus,
                upperOrNull(trimmedString(row, idx.get("nationality"))),
                trimmedString(row, idx.get("nationalId")),
                trimmedString(row, idx.get("email")),
                trimmedString(row, idx.get("phone")),
                hireDate,
                trimmedString(row, idx.get("departmentName")),
                trimmedString(row, idx.get("positionTitle")),
                trimmedString(row, idx.get("costCentre")),
                null, null, null, // orgUnitId, positionId, managerId — not in bulk import scope
                null, null, null, // delegate fields
                empType,
                decimalValue(row, idx.get("ftePercent")),
                null,             // leaveGroupId — not in bulk import scope
                null, null,       // M75: payrollGroupId, matrixManagerId — not in bulk import scope
                null,             // M78: rehireEligible — null defaults to true
                // M132 — Section 1 cosmetic fields (not in bulk import scope yet)
                trimmedString(row, idx.get("preferredName")),
                trimmedString(row, idx.get("placeOfBirth")),
                trimmedString(row, idx.get("bloodGroup")),
                trimmedString(row, idx.get("religion")),
                trimmedString(row, idx.get("nativeLanguage")),
                // M133 — Section 3 contact fields (importable when columns are present)
                trimmedString(row, idx.get("altPhone")),
                trimmedString(row, idx.get("workEmail")),
                trimmedString(row, idx.get("workPhone")),
                trimmedString(row, idx.get("extension")),
                trimmedString(row, idx.get("deskNumber")),
                // M134 — Section 4 employment fields
                trimmedString(row, idx.get("employeeCategory")),
                dateValue(row, idx.get("seniorityDate")));
    }

    // ── Cell reading helpers ──────────────────────────────────────────────────

    private static boolean isBlank(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String s = stringValue(cell);
                if (s != null && !s.isBlank()) return false;
            }
        }
        return true;
    }

    private static String trimmedString(Row row, Integer columnIndex) {
        if (columnIndex == null) return null;
        Cell cell = row.getCell(columnIndex);
        if (cell == null) return null;
        String v = stringValue(cell);
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String stringValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : trimDecimal(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    /** "21.0" → "21", "21.5" → "21.5" — keeps the import-string form predictable. */
    private static String trimDecimal(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    private static LocalDate dateValue(Row row, Integer columnIndex) {
        if (columnIndex == null) return null;
        Cell cell = row.getCell(columnIndex);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String s = trimmedString(row, columnIndex);
        if (s == null) return null;
        try {
            return LocalDate.parse(s);
        } catch (RuntimeException ex) {
            throw new RowParseException(
                    "Invalid date value: '" + s + "' (expected ISO yyyy-MM-dd)",
                    List.of());
        }
    }

    private static BigDecimal decimalValue(Row row, Integer columnIndex) {
        if (columnIndex == null) return null;
        Cell cell = row.getCell(columnIndex);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String s = trimmedString(row, columnIndex);
        if (s == null) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException ex) {
            throw new RowParseException("Invalid numeric value: '" + s + "'", List.of());
        }
    }

    private static <E extends Enum<E>> E enumValue(Row row, Integer columnIndex, Class<E> type) {
        String s = trimmedString(row, columnIndex);
        if (s == null) return null;
        try {
            return Enum.valueOf(type, s.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RowParseException(
                    "Invalid " + type.getSimpleName() + " value: '" + s + "'",
                    List.of(type.getSimpleName()));
        }
    }

    private static String upperOrNull(String s) {
        return s == null ? null : s.toUpperCase();
    }

    /** Internal — converted to a {@link RowError} at the boundary. */
    private static final class RowParseException extends RuntimeException {
        private final List<String> fields;
        RowParseException(String message, List<String> fields) {
            super(message);
            this.fields = fields;
        }
        List<String> getFields() { return fields; }
    }
}
