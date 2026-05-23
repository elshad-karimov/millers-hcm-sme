package az.millers.hcm.reporting.export;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class XlsxReportRenderer {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public byte[] render(String title, List<ReportSection> sections) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle titleStyle    = titleStyle(wb);
            CellStyle stampStyle    = stampStyle(wb);
            CellStyle sectionStyle  = sectionStyle(wb);
            CellStyle headerStyle   = headerStyle(wb);
            CellStyle labelStyle    = labelStyle(wb);
            CellStyle cellStyle     = cellStyle(wb);

            Set<String> usedNames = new HashSet<>();
            for (ReportSection section : sections) {
                String sheetName = uniqueSheetName(section.title(), usedNames);
                Sheet sheet = wb.createSheet(sheetName);

                int rowIdx = 0;
                Row titleRow = sheet.createRow(rowIdx++);
                writeCell(titleRow, 0, title + " — " + section.title(), titleStyle);
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

                Row stamp = sheet.createRow(rowIdx++);
                writeCell(stamp, 0, "Generated " + OffsetDateTime.now().format(STAMP), stampStyle);
                sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 6));

                rowIdx++; // spacer

                // Summary block
                if (!section.summary().isEmpty()) {
                    for (Map.Entry<String, String> e : section.summary().entrySet()) {
                        Row r = sheet.createRow(rowIdx++);
                        writeCell(r, 0, e.getKey(), labelStyle);
                        writeCell(r, 1, e.getValue(), cellStyle);
                    }
                    rowIdx++; // spacer
                }

                // Tables
                for (ReportTable t : section.tables()) {
                    Row tTitle = sheet.createRow(rowIdx++);
                    writeCell(tTitle, 0, t.title(), sectionStyle);
                    sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0,
                            Math.max(0, t.headers().size() - 1)));

                    Row hRow = sheet.createRow(rowIdx++);
                    for (int c = 0; c < t.headers().size(); c++) {
                        writeCell(hRow, c, t.headers().get(c), headerStyle);
                    }
                    if (t.rows().isEmpty()) {
                        Row empty = sheet.createRow(rowIdx++);
                        writeCell(empty, 0, "— no data —", cellStyle);
                    } else {
                        for (List<Object> row : t.rows()) {
                            Row dataRow = sheet.createRow(rowIdx++);
                            for (int c = 0; c < row.size(); c++) {
                                writeTyped(dataRow, c, row.get(c), cellStyle);
                            }
                        }
                    }
                    rowIdx++; // spacer between tables
                }

                int colCount = section.tables().stream()
                        .mapToInt(t -> t.headers().size()).max().orElse(2);
                for (int c = 0; c < colCount; c++) {
                    sheet.autoSizeColumn(c);
                    // Pad a touch so trimmed names breathe.
                    int width = Math.min(20000, sheet.getColumnWidth(c) + 600);
                    sheet.setColumnWidth(c, width);
                }
            }

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("XLSX generation failed: " + e.getMessage(), e);
        }
    }

    private static String uniqueSheetName(String desired, Set<String> used) {
        String base = desired.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        if (base.length() > 28) base = base.substring(0, 28);
        String candidate = base;
        int i = 2;
        while (used.contains(candidate.toLowerCase())) {
            String suffix = " (" + i + ")";
            int max = 31 - suffix.length();
            String trimmed = base.length() > max ? base.substring(0, max) : base;
            candidate = trimmed + suffix;
            i++;
        }
        used.add(candidate.toLowerCase());
        return candidate;
    }

    private static void writeCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) cell.setCellStyle(style);
    }

    /** Sets a cell from a typed value, using numeric cells where possible. */
    private static void writeTyped(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof BigDecimal bd) {
            cell.setCellValue(bd.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b);
        } else {
            cell.setCellValue(value.toString());
        }
        if (style != null) cell.setCellStyle(style);
    }

    // ---------- styles ----------

    private CellStyle titleStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        s.setFont(f);
        return s;
    }

    private CellStyle stampStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setItalic(true);
        f.setFontHeightInPoints((short) 9);
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFont(f);
        return s;
    }

    private CellStyle sectionStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 11);
        s.setFont(f);
        return s;
    }

    private CellStyle headerStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new java.awt.Color(91, 63, 229), null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyBorders(s);
        return s;
    }

    private CellStyle labelStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new java.awt.Color(244, 244, 248), null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyBorders(s);
        return s;
    }

    private CellStyle cellStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFont(wb.createFont());
        applyBorders(s);
        return s;
    }

    private static void applyBorders(CellStyle s) {
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    // POI's Font import collides with the one used elsewhere; this static keeps it bound.
    @SuppressWarnings("unused")
    private static final Class<?> FONT_TYPE = Font.class;
}
