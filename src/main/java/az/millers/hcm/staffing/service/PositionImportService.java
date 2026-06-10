package az.millers.hcm.staffing.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.api.dto.PositionRequest;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * M255 — Phase H: Excel bulk import for positions (PRD §46).
 *
 * <p>The contract:
 *   1. Operator downloads the template via {@link PositionImportXlsxTemplate}.
 *   2. Fills in the rows; uploads via {@link #preview} for validation.
 *   3. Fixes the errors; uploads again via {@link #commit} to apply.
 *
 * <p>Both {@code preview} and {@code commit} run the same validation;
 * {@code commit} additionally creates the rows in a single transaction
 * — any error aborts the whole batch so the DB doesn't end up in a
 * half-imported state.
 */
@Service
public class PositionImportService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "PositionImport";

    // Column order in the template — keep in sync with PositionImportXlsxTemplate.
    private static final int COL_CODE              = 0;
    private static final int COL_TITLE             = 1;
    private static final int COL_ORG_UNIT_LABEL    = 2;
    private static final int COL_GRADE             = 3;
    private static final int COL_JOB_FAMILY        = 4;
    private static final int COL_JOB_LEVEL         = 5;
    private static final int COL_APPROVED_HC       = 6;
    private static final int COL_SALARY_MIN        = 7;
    private static final int COL_SALARY_MAX        = 8;
    private static final int COL_CURRENCY          = 9;
    private static final int COL_EMPLOYMENT_TYPE   = 10;
    private static final int COL_COST_CENTRE       = 11;
    private static final int COL_BUDGET_CODE       = 12;
    private static final int COL_LOCATION          = 13;
    private static final int LAST_COL              = COL_LOCATION;

    private final PositionRepository positions;
    private final StaffingService staffingService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PositionImportService(PositionRepository positions,
                                  StaffingService staffingService,
                                  AuditService audit,
                                  CurrentRequest currentRequest) {
        this.positions = positions;
        this.staffingService = staffingService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Reads / dry-run ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ImportResult preview(MultipartFile file) {
        return parseAndValidate(file);
    }

    // ── Commit ─────────────────────────────────────────────────────────

    @Transactional
    public ImportResult commit(MultipartFile file) {
        ImportResult parsed = parseAndValidate(file);
        if (parsed.totalErrors() > 0) {
            // Re-throw as a friendly error rather than committing a partial
            // batch. The SPA shows the error rows so the operator can fix
            // and re-upload.
            throw new BadRequestException(
                    "Import has " + parsed.totalErrors() + " error(s) — fix and re-upload");
        }
        // All rows valid — create each position via the existing
        // StaffingService so audit, lifecycle defaults, vacancy state
        // derivation and counter init all fire on the same code path
        // as direct UI creation.
        List<UUID> createdIds = new ArrayList<>();
        for (RowResult r : parsed.rows()) {
            Position p = staffingService.create(r.toRequest());
            createdIds.add(p.getId());
        }
        audit.record(MODULE, ENTITY, "bulk-" + currentRequest.username(),
                "COMMIT", null,
                java.util.Map.of(
                        "rows", createdIds.size(),
                        "uploadedBy", currentRequest.username()));
        return parsed;  // operator sees the same per-row breakdown after commit
    }

    // ── Parse + validate (shared by preview + commit) ──────────────────

    private ImportResult parseAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file uploaded");
        }
        List<RowResult> rows = new ArrayList<>();
        // The "code" column is informational only — actual position codes
        // are auto-generated by the sequence. We still warn on duplicates
        // within the file so operators don't accidentally import two rows
        // they thought were one.
        Set<String> codesSeenInFile = new HashSet<>();

        try (InputStream in = file.getInputStream();
             Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            // First row is the header — skip.
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowBlank(row)) continue;
                RowResult result = parseRow(row, r + 1);  // 1-based for humans
                if (result.referenceCode() != null && !result.referenceCode().isBlank()) {
                    if (!codesSeenInFile.add(result.referenceCode().toLowerCase())) {
                        result.errors().add("Duplicate reference code within this file");
                    }
                }
                rows.add(result);
            }
        } catch (IOException ex) {
            throw new BadRequestException("Failed to read Excel: " + ex.getMessage());
        }
        return new ImportResult(rows);
    }

    private RowResult parseRow(Row row, int humanRowNum) {
        List<String> errors = new ArrayList<>();
        // "code" in the template is the operator's external reference
        // (e.g. their legacy HRIS code). It's stashed for the preview UI
        // and warning on duplicates but NOT persisted — position codes
        // are auto-generated by the sequence.
        String referenceCode = trim(stringCell(row, COL_CODE));
        String title = trim(stringCell(row, COL_TITLE));
        if (title == null || title.isBlank()) errors.add("title is required");
        Integer headcount = intCell(row, COL_APPROVED_HC, errors, "approvedHeadcount");
        if (headcount != null && headcount < 0) errors.add("approvedHeadcount must be >= 0");
        if (headcount == null) headcount = 1;  // default

        BigDecimal salaryMin = decimalCell(row, COL_SALARY_MIN, errors, "salaryMin");
        BigDecimal salaryMax = decimalCell(row, COL_SALARY_MAX, errors, "salaryMax");
        if (salaryMin != null && salaryMax != null
                && salaryMin.compareTo(salaryMax) > 0) {
            errors.add("salaryMin cannot be greater than salaryMax");
        }
        String currency = trim(stringCell(row, COL_CURRENCY));
        if (currency != null && !currency.isBlank() && currency.length() != 3) {
            errors.add("currency must be a 3-letter ISO code (e.g. AZN, USD)");
        }

        return new RowResult(
                humanRowNum,
                referenceCode, title,
                trim(stringCell(row, COL_ORG_UNIT_LABEL)),
                trim(stringCell(row, COL_GRADE)),
                trim(stringCell(row, COL_JOB_FAMILY)),
                trim(stringCell(row, COL_JOB_LEVEL)),
                headcount,
                salaryMin, salaryMax,
                currency,
                trim(stringCell(row, COL_EMPLOYMENT_TYPE)),
                trim(stringCell(row, COL_COST_CENTRE)),
                trim(stringCell(row, COL_BUDGET_CODE)),
                trim(stringCell(row, COL_LOCATION)),
                errors);
    }

    // ── Cell helpers — POI cells are wildly under-specified ────────────

    private static String stringCell(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null) return null;
        if (c.getCellType() == CellType.STRING) return c.getStringCellValue();
        if (c.getCellType() == CellType.NUMERIC) {
            // The user may have typed "POS-00001" into an inferred-numeric
            // cell. Trim the trailing ".0" that POI adds.
            double v = c.getNumericCellValue();
            if (v == Math.floor(v)) return String.valueOf((long) v);
            return String.valueOf(v);
        }
        if (c.getCellType() == CellType.BOOLEAN) return String.valueOf(c.getBooleanCellValue());
        return null;
    }

    private static Integer intCell(Row row, int col, List<String> errors, String field) {
        Cell c = row.getCell(col);
        if (c == null) return null;
        try {
            if (c.getCellType() == CellType.NUMERIC) return (int) c.getNumericCellValue();
            String s = stringCell(row, col);
            if (s == null || s.isBlank()) return null;
            return Integer.parseInt(s.trim());
        } catch (RuntimeException ex) {
            errors.add(field + " must be a whole number");
            return null;
        }
    }

    private static BigDecimal decimalCell(Row row, int col, List<String> errors, String field) {
        Cell c = row.getCell(col);
        if (c == null) return null;
        try {
            if (c.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(c.getNumericCellValue());
            }
            String s = stringCell(row, col);
            if (s == null || s.isBlank()) return null;
            return new BigDecimal(s.trim());
        } catch (RuntimeException ex) {
            errors.add(field + " must be a number");
            return null;
        }
    }

    private static boolean isRowBlank(Row row) {
        for (int c = 0; c <= LAST_COL; c++) {
            Cell cell = row.getCell(c);
            if (cell == null) continue;
            if (cell.getCellType() == CellType.BLANK) continue;
            if (cell.getCellType() == CellType.STRING
                    && cell.getStringCellValue().isBlank()) continue;
            return false;
        }
        return true;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    // ── Result records ─────────────────────────────────────────────────

    public record RowResult(
            int rowNumber,
            /** Operator's external reference; NOT persisted (codes are auto-generated). */
            String referenceCode,
            String title,
            String orgUnitLabel, String grade,
            String jobFamily, String jobLevel,
            int approvedHeadcount,
            BigDecimal salaryMin, BigDecimal salaryMax,
            String currency,
            String employmentType, String costCentre, String budgetCode, String location,
            List<String> errors) {

        public boolean valid() { return errors.isEmpty(); }

        /** Build a PositionRequest for {@link StaffingService#create}. */
        public PositionRequest toRequest() {
            return new PositionRequest(
                    title,
                    null,           // parentPositionId — not in template (linked later)
                    null,           // orgUnitId
                    orgUnitLabel,
                    grade, jobFamily, jobLevel,
                    approvedHeadcount,
                    salaryMin, salaryMax,
                    currency == null || currency.isBlank() ? "AZN" : currency,
                    employmentType, costCentre, budgetCode, location,
                    null, null,     // effectiveFrom / effectiveTo
                    // M254 compliance fields — null on import; HR can fill
                    // them in afterwards if needed.
                    null, null, null, null, null, null, null);
        }
    }

    public record ImportResult(List<RowResult> rows) {
        public int totalRows() { return rows.size(); }
        public int validRows() { return (int) rows.stream().filter(RowResult::valid).count(); }
        public int errorRows() { return totalRows() - validRows(); }
        public int totalErrors() {
            return rows.stream().mapToInt(r -> r.errors().size()).sum();
        }
    }
}
