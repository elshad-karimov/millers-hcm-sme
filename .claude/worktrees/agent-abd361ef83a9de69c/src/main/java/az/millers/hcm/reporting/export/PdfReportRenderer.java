package az.millers.hcm.reporting.export;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Component
public class PdfReportRenderer {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
    private static final Font H_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font CELL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font LABEL_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

    public byte[] render(String title, List<ReportSection> sections) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 48, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph header = new Paragraph(title, TITLE_FONT);
            header.setSpacingAfter(4);
            doc.add(header);

            Paragraph stamp = new Paragraph(
                    "Generated " + OffsetDateTime.now().format(STAMP),
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9));
            stamp.setSpacingAfter(16);
            doc.add(stamp);

            for (ReportSection section : sections) {
                doc.add(new Paragraph(section.title(), SECTION_FONT));
                doc.add(Chunk.NEWLINE);

                if (!section.summary().isEmpty()) {
                    PdfPTable summary = new PdfPTable(2);
                    summary.setWidthPercentage(60);
                    summary.setHorizontalAlignment(Element.ALIGN_LEFT);
                    summary.setSpacingAfter(12);
                    for (Map.Entry<String, String> e : section.summary().entrySet()) {
                        summary.addCell(labelCell(e.getKey()));
                        summary.addCell(valueCell(e.getValue()));
                    }
                    doc.add(summary);
                }

                for (ReportTable t : section.tables()) {
                    doc.add(new Paragraph(t.title(),
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
                    doc.add(Chunk.NEWLINE);
                    doc.add(buildTable(t));
                    doc.add(Chunk.NEWLINE);
                }
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    private PdfPTable buildTable(ReportTable t) {
        PdfPTable table = new PdfPTable(t.headers().size());
        table.setWidthPercentage(100);
        for (String h : t.headers()) {
            PdfPCell c = new PdfPCell(new Phrase(h, H_FONT));
            c.setBackgroundColor(new java.awt.Color(91, 63, 229)); // brand purple
            c.setHorizontalAlignment(Element.ALIGN_LEFT);
            c.setPadding(5);
            c.setBorder(Rectangle.BOX);
            c.setBorderColor(new java.awt.Color(220, 220, 220));
            // Make header text white
            c.setPhrase(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9,
                    new java.awt.Color(255, 255, 255))));
            table.addCell(c);
        }
        boolean stripe = false;
        for (List<Object> row : t.rows()) {
            for (Object cell : row) {
                PdfPCell c = new PdfPCell(new Phrase(cell == null ? "—" : cell.toString(), CELL_FONT));
                c.setPadding(4);
                c.setBorderColor(new java.awt.Color(230, 230, 230));
                if (stripe) c.setBackgroundColor(new java.awt.Color(248, 248, 252));
                table.addCell(c);
            }
            stripe = !stripe;
        }
        if (t.rows().isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("— no data —",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, new java.awt.Color(140, 140, 140))));
            empty.setColspan(t.headers().size());
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setPadding(6);
            table.addCell(empty);
        }
        return table;
    }

    private PdfPCell labelCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, LABEL_FONT));
        c.setPadding(4);
        c.setBorderColor(new java.awt.Color(230, 230, 230));
        c.setBackgroundColor(new java.awt.Color(244, 244, 248));
        return c;
    }

    private PdfPCell valueCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, CELL_FONT));
        c.setPadding(4);
        c.setBorderColor(new java.awt.Color(230, 230, 230));
        return c;
    }
}
