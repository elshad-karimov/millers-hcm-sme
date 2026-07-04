package az.millers.hcm.corehr.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import az.millers.hcm.corehr.domain.Employee;

/**
 * M132 — pins the QR encoder + payload + size-clamp rules. Mockito-free
 * because Java 25 class-file v69 isn't supported by Byte Buddy.
 */
class EmployeeQrCodeServiceTest {

    private static Employee employee(UUID id, String no) {
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeNo(no);
        return e;
    }

    // ── payloadFor ────────────────────────────────────────────────────────

    @Test
    void payloadEncodesIdAndEmployeeNo() {
        UUID id = UUID.randomUUID();
        Employee e = employee(id, "EMP-00042");
        assertThat(EmployeeQrCodeService.payloadFor(e))
                .isEqualTo("MILLERS-HCM|" + id + "|EMP-00042");
    }

    // ── clampSize ─────────────────────────────────────────────────────────

    @Test
    void clampSizeDefaultsOnZero() {
        assertThat(EmployeeQrCodeService.clampSize(0))
                .isEqualTo(EmployeeQrCodeService.DEFAULT_SIZE);
    }

    @Test
    void clampSizeFloorAt50() {
        assertThat(EmployeeQrCodeService.clampSize(20)).isEqualTo(50);
        assertThat(EmployeeQrCodeService.clampSize(50)).isEqualTo(50);
    }

    @Test
    void clampSizeCapAtMax() {
        assertThat(EmployeeQrCodeService.clampSize(EmployeeQrCodeService.MAX_SIZE + 100))
                .isEqualTo(EmployeeQrCodeService.MAX_SIZE);
    }

    @Test
    void clampSizePassesThroughInsideRange() {
        assertThat(EmployeeQrCodeService.clampSize(256)).isEqualTo(256);
    }

    // ── encode ────────────────────────────────────────────────────────────

    @Test
    void encodeProducesPngOfRequestedSize() throws Exception {
        byte[] bytes = EmployeeQrCodeService.encode("hello", 300);
        // PNG magic bytes.
        assertThat(bytes[0] & 0xFF).isEqualTo(0x89);
        assertThat(new byte[]{bytes[1], bytes[2], bytes[3]})
                .isEqualTo(new byte[]{'P', 'N', 'G'});
        // ImageIO can re-read it back at the expected size.
        var img = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(img).isNotNull();
        assertThat(img.getWidth()).isEqualTo(300);
        assertThat(img.getHeight()).isEqualTo(300);
    }

    @Test
    void encodeIsDeterministicForSameInputs() {
        byte[] a = EmployeeQrCodeService.encode("MILLERS-HCM|abc|EMP-1", 200);
        byte[] b = EmployeeQrCodeService.encode("MILLERS-HCM|abc|EMP-1", 200);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void encodeDiffersWhenPayloadChanges() {
        byte[] a = EmployeeQrCodeService.encode("MILLERS-HCM|abc|EMP-1", 200);
        byte[] b = EmployeeQrCodeService.encode("MILLERS-HCM|abc|EMP-2", 200);
        assertThat(a).isNotEqualTo(b);
    }
}
