package az.millers.hcm.corehr.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;

/**
 * M132 — generates a QR code PNG for an employee badge.
 *
 * <p>Payload encodes a stable identifier and the employee's number,
 * not PII: a scanner hits the public verify endpoint at
 * {@code /api/public/employees/verify} (M209) without ever decoding
 * personal data from the QR. Format:
 * <pre>MILLERS-HCM|{employeeId}|{employeeNo}</pre>
 *
 * <p>The image is generated on the fly — no storage. Each call costs
 * ~5–10ms for the default 300×300 module which is fine even from a
 * dashboard widget.
 */
@Service
public class EmployeeQrCodeService {

    /** Default badge size. 300×300 prints crisply on a 25mm sticker. */
    public static final int DEFAULT_SIZE = 300;

    /** Hard cap so a malicious caller can't ask for a 10000×10000 image. */
    public static final int MAX_SIZE = 1024;

    private final EmployeeRepository employees;

    public EmployeeQrCodeService(EmployeeRepository employees) {
        this.employees = employees;
    }

    /**
     * Renders the QR code as a PNG byte array. Resolves the employee
     * first so we 404 cleanly on unknown IDs.
     */
    public byte[] renderPng(UUID employeeId, int size) {
        Employee e = employees.findById(employeeId).orElseThrow(
                () -> new ResourceNotFoundException("Employee not found: " + employeeId));
        int clamped = clampSize(size);
        return encode(payloadFor(e), clamped);
    }

    /** Pure-static for the test pass. */
    public static String payloadFor(Employee e) {
        return "MILLERS-HCM|" + e.getId() + "|" + e.getEmployeeNo();
    }

    /** Pure-static. Clamps to {@code [50, MAX_SIZE]}. */
    public static int clampSize(int requested) {
        if (requested <= 0) return DEFAULT_SIZE;
        if (requested < 50) return 50;
        if (requested > MAX_SIZE) return MAX_SIZE;
        return requested;
    }

    /**
     * Pure-static QR-encoder. Returned PNG bytes are deterministic for
     * the same (payload, size) — useful for the test pass.
     */
    public static byte[] encode(String payload, int size) {
        try {
            var hints = new java.util.EnumMap<EncodeHintType, Object>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new MultiFormatWriter()
                    .encode(payload, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(MatrixToImageWriter.toBufferedImage(matrix), "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("QR encode failed", ex);
        }
    }
}
