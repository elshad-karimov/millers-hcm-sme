package az.millers.hcm.letters.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import az.millers.hcm.corehr.service.EmployeeQrCodeService;
import az.millers.hcm.letters.domain.LetterRequest;
import az.millers.hcm.letters.domain.LetterTemplate;

/**
 * M139 — renders a {@link LetterRequest} as a PDF with:
 *
 * <ul>
 *   <li>Letterhead band (title + request-no + issue date)</li>
 *   <li>Substituted body text from {@link LetterRenderer}</li>
 *   <li>Signature line — name + date + role label</li>
 *   <li>QR code in the bottom-right corner pointing at the public
 *       verify endpoint</li>
 * </ul>
 *
 * <p>OpenPDF is already on the classpath via the report-exports
 * dependency (M77 era). ZXing comes from the M132 dependency. No new
 * classpath additions in M139.
 */
@Component
public class LetterPdfRenderer {

    private static final SecureRandom RND = new SecureRandom();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Public-facing host the QR code points at. Defaults to a relative
     * path so the SPA verify page resolves correctly regardless of
     * deployment; HR can override via configuration to embed a full
     * URL when sharing letters with third parties.
     */
    @Value("${letters.verify-base-url:/verify/letter}")
    private String verifyBaseUrl;

    /**
     * Generate a fresh verification token. 32 url-safe characters,
     * stored on the {@code verification_token} column.
     */
    public static String newToken() {
        byte[] bytes = new byte[24];
        RND.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Render the letter PDF as a byte array. Returns the bytes ready
     * to upload to MinIO + serve via the download endpoint.
     */
    public byte[] render(LetterRequest req, LetterTemplate template, String renderedBody) {
        return renderDocument(
                template.getName(),
                req.getRequestNo(),
                req.getIssuedAt() == null
                        ? LocalDate.now() : req.getIssuedAt().toLocalDate(),
                template.getLanguage(),
                renderedBody,
                req.getSignedBy() == null ? "Human Resources Department" : req.getSignedBy(),
                req.getSignedAt() == null
                        ? LocalDate.now() : req.getSignedAt().toLocalDate(),
                req.getVerificationToken());
    }

    /**
     * M283 — generic document layout (letterhead / body / signature /
     * optional verify-QR). One layout shared by HR letters (M139) and
     * offer letters (M283) so a branding change lands in ONE place.
     *
     * @param verifyToken nullable — QR omitted when null
     */
    public byte[] renderDocument(String title, String referenceNo, LocalDate issuedOn,
                                  String language, String renderedBody,
                                  String signerName, LocalDate signedOn,
                                  String verifyToken) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Document doc = new Document(PageSize.A4, 56, 56, 72, 72)) {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // ── Letterhead ────────────────────────────────────────────
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
            Paragraph titleP = new Paragraph(title, titleFont);
            titleP.setAlignment(Element.ALIGN_CENTER);
            doc.add(titleP);
            Paragraph meta = new Paragraph(
                    referenceNo + "  •  Issued " + issuedOn.format(ISO)
                            + (language == null ? "" : "  •  Locale: " + language),
                    metaFont);
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.setSpacingAfter(18);
            doc.add(meta);

            // ── Body ──────────────────────────────────────────────────
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
            for (String line : renderedBody.split("\n")) {
                Paragraph p = new Paragraph(line.isEmpty() ? " " : line, body);
                p.setSpacingAfter(2);
                doc.add(p);
            }

            // ── Signature + QR table ─────────────────────────────────
            doc.add(Chunk.NEWLINE);
            PdfPTable footer = new PdfPTable(2);
            footer.setWidthPercentage(100);
            footer.setWidths(new int[]{ 65, 35 });

            Font sigLabel = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.DARK_GRAY);
            Font sigName = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);

            PdfPCell sigCell = new PdfPCell();
            sigCell.setBorder(0);
            sigCell.addElement(new Paragraph("\n_________________________", body));
            sigCell.addElement(new Paragraph(signerName, sigName));
            sigCell.addElement(new Paragraph("Signed on " + signedOn.format(ISO), sigLabel));
            footer.addCell(sigCell);

            PdfPCell qrCell = new PdfPCell();
            qrCell.setBorder(0);
            qrCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            if (verifyToken != null) {
                String payload = verifyBaseUrl + "/" + verifyToken;
                byte[] qr = EmployeeQrCodeService.encode(payload, 110);
                Image qrImg = Image.getInstance(qr);
                qrImg.scaleAbsolute(80, 80);
                qrCell.addElement(qrImg);
                Phrase caption = new Phrase("Scan to verify", metaFont);
                Paragraph cap = new Paragraph(caption);
                cap.setAlignment(Element.ALIGN_CENTER);
                qrCell.addElement(cap);
            }
            footer.addCell(qrCell);

            doc.add(footer);
        } catch (DocumentException | java.io.IOException ex) {
            throw new IllegalStateException("Letter PDF render failed", ex);
        }
        return out.toByteArray();
    }
}
