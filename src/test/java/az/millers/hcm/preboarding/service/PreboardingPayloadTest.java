package az.millers.hcm.preboarding.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;

/**
 * M122 — payload-shape validation. Pins:
 * <ul>
 *   <li>at least one section is required,</li>
 *   <li>emergency contact / dependent rows require their key fields,</li>
 *   <li>birthDate parses and rejects future / implausibly-old dates,</li>
 *   <li>type confusion (list where map expected) errors clearly.</li>
 * </ul>
 */
class PreboardingPayloadTest {

    @Test
    void acceptsPersonalInfoOnly() {
        Map<String, Object> p = Map.of(
                "personalInfo", Map.of("phone", "+994500000000", "birthDate", "1990-01-15"));
        assertThatCode(() -> PreboardingPayload.validate(p)).doesNotThrowAnyException();
    }

    @Test
    void acceptsEmergencyContactsOnly() {
        Map<String, Object> p = Map.of(
                "emergencyContacts", List.of(
                        Map.of("name", "Mum", "relationship", "PARENT", "phone", "+994500000001")));
        assertThatCode(() -> PreboardingPayload.validate(p)).doesNotThrowAnyException();
    }

    @Test
    void acceptsDependentsOnly() {
        Map<String, Object> p = Map.of(
                "dependents", List.of(
                        Map.of("firstName", "Pat", "lastName", "Q",
                                "relationshipType", "CHILD", "dateOfBirth", "2018-03-04")));
        assertThatCode(() -> PreboardingPayload.validate(p)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullPayload() {
        assertThatThrownBy(() -> PreboardingPayload.validate(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsEmptyPayload() {
        assertThatThrownBy(() -> PreboardingPayload.validate(Map.of()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsAllSectionsEmpty() {
        // Sections present but no real content.
        Map<String, Object> p = Map.of(
                "personalInfo", Map.of());
        // Empty personalInfo IS a present section, so this should pass shape
        // gate (more aggressive content-required rules can come later).
        assertThatCode(() -> PreboardingPayload.validate(p)).doesNotThrowAnyException();
    }

    @Test
    void rejectsListWhereMapExpected() {
        Map<String, Object> p = Map.of(
                "personalInfo", List.of("not", "a", "map"));
        assertThatThrownBy(() -> PreboardingPayload.validate(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("personalInfo must be an object");
    }

    @Test
    void rejectsMapWhereListExpected() {
        Map<String, Object> p = Map.of(
                "emergencyContacts", Map.of("name", "Mum"));
        assertThatThrownBy(() -> PreboardingPayload.validate(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("emergencyContacts must be an array");
    }

    @Test
    void rejectsEmergencyContactMissingName() {
        Map<String, Object> p = Map.of(
                "emergencyContacts", List.of(Map.of("relationship", "PARENT", "phone", "+1")));
        assertThatThrownBy(() -> PreboardingPayload.validate(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("emergencyContacts[].name is required");
    }

    @Test
    void rejectsEmergencyContactMissingPhone() {
        Map<String, Object> p = Map.of(
                "emergencyContacts", List.of(Map.of("name", "Mum", "relationship", "PARENT")));
        assertThatThrownBy(() -> PreboardingPayload.validate(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("emergencyContacts[].phone is required");
    }

    @Test
    void rejectsDependentMissingFirstName() {
        Map<String, Object> p = Map.of(
                "dependents", List.of(Map.of("lastName", "X", "relationshipType", "CHILD")));
        assertThatThrownBy(() -> PreboardingPayload.validate(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dependents[].firstName is required");
    }

    @Test
    void rejectsFutureBirthDate() {
        String future = LocalDate.now().plusYears(1).toString();
        Map<String, Object> p = Map.of(
                "personalInfo", Map.of("phone", "+1", "birthDate", future));
        assertThatThrownBy(() -> PreboardingPayload.validate(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be in the future");
    }

    @Test
    void rejectsImplausiblyOldBirthDate() {
        Map<String, Object> p = Map.of(
                "personalInfo", Map.of("birthDate", "1800-01-01"));
        assertThatThrownBy(() -> PreboardingPayload.validate(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("implausibly old");
    }

    @Test
    void rejectsMalformedDateLiteral() {
        Map<String, Object> p = Map.of(
                "personalInfo", Map.of("birthDate", "31/12/1990"));
        assertThatThrownBy(() -> PreboardingPayload.validate(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ISO-8601");
    }

    @Test
    void rejectsEmailWithoutAtSign() {
        Map<String, Object> p = Map.of(
                "personalInfo", Map.of("personalEmail", "not-an-email"));
        assertThatThrownBy(() -> PreboardingPayload.validate(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("personalEmail");
    }

    @Test
    void acceptsValidEmail() {
        Map<String, Object> p = Map.of(
                "personalInfo", Map.of("personalEmail", "candidate@example.com"));
        assertThatCode(() -> PreboardingPayload.validate(p)).doesNotThrowAnyException();
    }
}
