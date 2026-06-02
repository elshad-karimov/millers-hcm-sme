package az.millers.hcm.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskingTest {

    @Test
    void maskEmailKeepsFirstAndLastOfLocalPart() {
        assertThat(PiiMasking.maskEmail("aytan.mammadova@example.com"))
                .isEqualTo("a***a@example.com");
        assertThat(PiiMasking.maskEmail("ab@x.io")).isEqualTo("*b@x.io");
        assertThat(PiiMasking.maskEmail("x@y.io")).isEqualTo("*x@y.io");
    }

    @Test
    void maskEmailHandlesNullAndBlank() {
        assertThat(PiiMasking.maskEmail(null)).isNull();
        assertThat(PiiMasking.maskEmail("")).isNull();
        assertThat(PiiMasking.maskEmail("noatsign")).isEqualTo("***");
    }

    @Test
    void maskPhoneKeepsLastFourDigits() {
        assertThat(PiiMasking.maskPhone("+994 55 123 4567"))
                .isEqualTo("•••• •••• 4567");
        assertThat(PiiMasking.maskPhone("123")).isEqualTo("•••• 123");
        assertThat(PiiMasking.maskPhone(null)).isNull();
    }

    @Test
    void maskDocumentIdKeepsHeadAndTail() {
        assertThat(PiiMasking.maskDocumentId("AZE12345678")).isEqualTo("AZ•••78");
        assertThat(PiiMasking.maskDocumentId("ABCD")).isEqualTo("•••");
        assertThat(PiiMasking.maskDocumentId(null)).isNull();
    }

    @Test
    void maskIbanFormatsWithSpaces() {
        assertThat(PiiMasking.maskIban("AZ77 NABZ 0000 0000 1234 5678 0001"))
                .isEqualTo("AZ77 •••• •••• •••• 0001");
        assertThat(PiiMasking.maskIban("AZ77ABCD")).isEqualTo("••••");
    }

    @Test
    void maskAccountNumberKeepsLastFour() {
        assertThat(PiiMasking.maskAccountNumber("4111-1111-1111-9876"))
                .isEqualTo("••••9876");
        assertThat(PiiMasking.maskAccountNumber("12")).isEqualTo("••••");
    }
}
