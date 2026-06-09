package az.millers.hcm.staffing.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import az.millers.hcm.organization.repo.LegalEntityRepository;
import az.millers.hcm.staffing.domain.StaffingTable;
import az.millers.hcm.staffing.domain.StaffingTableLine;

/**
 * M245 — Render a staffing table to Excel using the standard
 * "Ştat cədvəli" layout:
 *
 * <pre>
 *   Title:  Ştat cədvəli — {Legal entity name} — version {versionCode}
 *   Period: {effectiveFrom} → {effectiveTo|open}
 *
 *   |  №  |  Struktur bölmə  |  Vəzifə (Position)  |  Dərəcə  |  Say  |  Maaş  |  Aylıq fond  |  Qeyd  |
 *   |--------------------------------------------------------------------------------------------------|
 *   | ... line rows ...                                                                                |
 *   |--------------------------------------------------------------------------------------------------|
 *   |                                                                Cəmi:  | sum | sum  |             |
 * </pre>
 *
 * Headers are bilingual (AZ + EN) to fit both the government form and
 * the SPA's translated UI.
 */
@Service
public class StaffingTableXlsxExport {

    private final StaffingTableService tables;
    private final LegalEntityRepository legalEntities;

    public StaffingTableXlsxExport(StaffingTableService tables,
                                    LegalEntityRepository legalEntities) {
        this.tables = tables;
        this.legalEntities = legalEntities;
    }

    public byte[] render(UUID staffingTableId) throws IOException {
        StaffingTable t = tables.get(staffingTableId);
        List<StaffingTableLine> lines = tables.linesFor(staffingTableId);

        String legalEntityName = legalEntities.findById(t.getLegalEntityId())
                .map(le -> le.getName() + " (" + le.getCode() + ")")
                .orElse("—");

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // ── Styles ──────────────────────────────────────────────
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            CellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            Font headerFont = wb.createFont();
            headerFont.setBold(true);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(headerStyle);

            CellStyle bodyStyle = wb.createCellStyle();
            border(bodyStyle);

            CellStyle moneyStyle = wb.createCellStyle();
            border(moneyStyle);
            var fmt = wb.createDataFormat();
            moneyStyle.setDataFormat(fmt.getFormat("#,##0.00"));
            moneyStyle.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle totalStyle = wb.createCellStyle();
            border(totalStyle);
            totalStyle.setFont(headerFont);
            totalStyle.setDataFormat(fmt.getFormat("#,##0.00"));
            totalStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalStyle.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle labelStyle = wb.createCellStyle();
            labelStyle.setFont(headerFont);
            labelStyle.setAlignment(HorizontalAlignment.RIGHT);
            border(labelStyle);
            labelStyle.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            labelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // ── Sheet ───────────────────────────────────────────────
            Sheet sheet = wb.createSheet("Ştat cədvəli");
            sheet.setColumnWidth(0, 1500);   // №
            sheet.setColumnWidth(1, 7000);   // Struktur bölmə
            sheet.setColumnWidth(2, 9000);   // Vəzifə
            sheet.setColumnWidth(3, 2500);   // Dərəcə
            sheet.setColumnWidth(4, 2000);   // Say
            sheet.setColumnWidth(5, 4000);   // Maaş
            sheet.setColumnWidth(6, 5000);   // Aylıq fond
            sheet.setColumnWidth(7, 6000);   // Qeyd

            int rowIdx = 0;

            // Title
            Row title = sheet.createRow(rowIdx++);
            var titleCell = title.createCell(0);
            titleCell.setCellValue(
                    "Ştat cədvəli — " + legalEntityName
                            + " — version " + t.getVersionCode());
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));
            title.setHeightInPoints(22f);

            Row period = sheet.createRow(rowIdx++);
            period.createCell(0).setCellValue("Period:");
            period.createCell(1).setCellValue(
                    t.getEffectiveFrom() + " → "
                            + (t.getEffectiveTo() == null ? "open" : t.getEffectiveTo().toString()));
            sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 1, 3));

            Row status = sheet.createRow(rowIdx++);
            status.createCell(0).setCellValue("Status:");
            status.createCell(1).setCellValue(t.getStatus().name());
            if (t.getApprovedBy() != null) {
                status.createCell(3).setCellValue("Approved by:");
                status.createCell(4).setCellValue(t.getApprovedBy());
            }
            rowIdx++;  // blank gap

            // Column headers — bilingual
            Row hdr = sheet.createRow(rowIdx++);
            hdr.setHeightInPoints(28f);
            String[] headers = {
                    "№",
                    "Struktur bölmə\n(Org unit)",
                    "Vəzifə\n(Position)",
                    "Dərəcə\n(Grade)",
                    "Say\n(Count)",
                    "Maaş\n(Salary)",
                    "Aylıq fond\n(Monthly fund)",
                    "Qeyd\n(Notes)",
            };
            for (int i = 0; i < headers.length; i++) {
                var c = hdr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // Body
            BigDecimal totalFund = BigDecimal.ZERO;
            int totalCount = 0;
            for (StaffingTableLine l : lines) {
                Row r = sheet.createRow(rowIdx++);
                var c0 = r.createCell(0); c0.setCellValue(l.getLineNo()); c0.setCellStyle(bodyStyle);
                var c1 = r.createCell(1); c1.setCellValue(nz(l.getOrgUnitLabel())); c1.setCellStyle(bodyStyle);
                var c2 = r.createCell(2); c2.setCellValue(nz(l.getPositionTitle())); c2.setCellStyle(bodyStyle);
                var c3 = r.createCell(3); c3.setCellValue(nz(l.getGrade())); c3.setCellStyle(bodyStyle);
                var c4 = r.createCell(4); c4.setCellValue(l.getApprovedHeadcount()); c4.setCellStyle(bodyStyle);
                var c5 = r.createCell(5); c5.setCellValue(l.getMonthlySalary().doubleValue()); c5.setCellStyle(moneyStyle);
                var c6 = r.createCell(6); c6.setCellValue(l.getMonthlySalaryFund().doubleValue()); c6.setCellStyle(moneyStyle);
                var c7 = r.createCell(7); c7.setCellValue(nz(l.getNotes())); c7.setCellStyle(bodyStyle);
                totalCount += l.getApprovedHeadcount();
                totalFund = totalFund.add(l.getMonthlySalaryFund());
            }

            // Totals row
            Row totals = sheet.createRow(rowIdx++);
            for (int i = 0; i < 4; i++) totals.createCell(i).setCellStyle(labelStyle);
            var lbl = totals.getCell(3);
            lbl.setCellValue("Cəmi (Total):");
            lbl.setCellStyle(labelStyle);
            var tCount = totals.createCell(4);
            tCount.setCellValue(totalCount);
            tCount.setCellStyle(totalStyle);
            totals.createCell(5).setCellStyle(totalStyle);
            var tFund = totals.createCell(6);
            tFund.setCellValue(totalFund.doubleValue());
            tFund.setCellStyle(totalStyle);
            totals.createCell(7).setCellStyle(totalStyle);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void border(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
