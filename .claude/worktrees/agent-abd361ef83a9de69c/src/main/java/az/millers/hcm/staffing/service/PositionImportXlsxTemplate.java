package az.millers.hcm.staffing.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * M255 — Downloadable Excel template for bulk position import.
 *
 * <p>Layout matches the column order in {@link PositionImportService}.
 * Header row is bold + grey; a second row with three sample positions
 * shows the operator the expected format ("you can delete these rows").
 */
@Service
public class PositionImportXlsxTemplate {

    /** Field name (header), example value for row 1, example for row 2, example for row 3. */
    private static final String[][] FIELDS = {
            {"code (ref only)", "FIN-ACC-001",    "FIN-ACC-002",    "HR-MGR-001"},
            {"title",           "Senior Accountant", "Junior Accountant", "HR Manager"},
            {"orgUnitLabel",    "Finance",        "Finance",        "Human Resources"},
            {"grade",           "G5",             "G3",             "G7"},
            {"jobFamily",       "Accounting",     "Accounting",     "HR"},
            {"jobLevel",        "Senior",         "Junior",         "Manager"},
            {"approvedHeadcount","1",             "2",              "1"},
            {"salaryMin",       "1800",           "1000",           "2500"},
            {"salaryMax",       "2400",           "1400",           "3500"},
            {"currency",        "AZN",            "AZN",            "AZN"},
            {"employmentType",  "FULL_TIME",      "FULL_TIME",      "FULL_TIME"},
            {"costCentre",      "CC-FIN",         "CC-FIN",         "CC-HR"},
            {"budgetCode",      "BUD-FIN-2026",   "BUD-FIN-2026",   "BUD-HR-2026"},
            {"location",        "Baku HQ",        "Baku HQ",        "Baku HQ"},
    };

    public byte[] render() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Positions");

            // ── Styles ──
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(headerStyle);

            CellStyle sampleStyle = wb.createCellStyle();
            sampleStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            sampleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(sampleStyle);

            // ── Header row ──
            Row header = sheet.createRow(0);
            for (int c = 0; c < FIELDS.length; c++) {
                var cell = header.createCell(c);
                cell.setCellValue(FIELDS[c][0]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(c, widthFor(c));
            }
            header.setHeightInPoints(22f);

            // ── Three sample rows ──
            for (int sampleRow = 0; sampleRow < 3; sampleRow++) {
                Row r = sheet.createRow(sampleRow + 1);
                for (int c = 0; c < FIELDS.length; c++) {
                    var cell = r.createCell(c);
                    String v = FIELDS[c][sampleRow + 1];
                    // Best-effort numeric typing — POI will coerce strings
                    // into a Number when the value parses cleanly.
                    if (isNumeric(v)) {
                        cell.setCellValue(Double.parseDouble(v));
                    } else {
                        cell.setCellValue(v);
                    }
                    cell.setCellStyle(sampleStyle);
                }
            }

            // ── Hint row ──
            Row hint = sheet.createRow(5);
            var hintCell = hint.createCell(0);
            hintCell.setCellValue(
                    "Delete the yellow sample rows above before uploading. "
                    + "Required: title, approvedHeadcount. "
                    + "'code' is informational — actual codes are auto-generated.");
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                    5, 5, 0, FIELDS.length - 1));

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static int widthFor(int c) {
        // approximate column widths in chars × 256
        switch (c) {
            case 0:  return 4500;   // code
            case 1:  return 7000;   // title
            case 2:  return 5000;   // orgUnitLabel
            case 3:  return 2500;   // grade
            case 4:  return 4500;   // jobFamily
            case 5:  return 3500;   // jobLevel
            case 6:  return 4500;   // approvedHeadcount
            case 7:  return 3500;   // salaryMin
            case 8:  return 3500;   // salaryMax
            case 9:  return 2500;   // currency
            case 10: return 4500;   // employmentType
            case 11: return 3500;   // costCentre
            case 12: return 4500;   // budgetCode
            default: return 4500;   // location
        }
    }

    private static boolean isNumeric(String v) {
        if (v == null) return false;
        try {
            Double.parseDouble(v);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static void border(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }
}
