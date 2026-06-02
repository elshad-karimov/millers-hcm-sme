package az.millers.hcm.corehr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.MaritalStatus;

class PersonalInfoFieldValidatorTest {

    private final PersonalInfoFieldValidator validator = new PersonalInfoFieldValidator();

    @Test
    void rejectsUnknownFields() {
        assertThatThrownBy(() -> validator.validate("salary", "9999"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not editable");
    }

    @Test
    void acceptsValidEmail() {
        // shouldn't throw
        validator.validate("email", "valid.address@example.com");
    }

    @Test
    void rejectsBadEmail() {
        assertThatThrownBy(() -> validator.validate("email", "not-an-email"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("valid address");
    }

    @Test
    void acceptsValidPhoneAndRejectsLetters() {
        validator.validate("phone", "+994 55 123 4567");
        assertThatThrownBy(() -> validator.validate("phone", "abcde"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void countryMustBeIsoAlpha2Upper() {
        validator.validate("country", "AZ");
        assertThatThrownBy(() -> validator.validate("country", "Azerbaijan"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> validator.validate("country", "az"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void maritalStatusMustMatchEnum() {
        validator.validate("maritalStatus", "MARRIED");
        assertThatThrownBy(() -> validator.validate("maritalStatus", "ENGAGED"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void nullValueIsAllowed() {
        // Clearing a field is intentional — no throw.
        validator.validate("email", null);
        validator.validate("phone", null);
    }

    @Test
    void applyMutatesEmployeeForKnownFields() {
        Employee e = new Employee();
        boolean mutated = validator.apply(e, "email", "new@example.com");
        assertThat(mutated).isTrue();
        assertThat(e.getEmail()).isEqualTo("new@example.com");

        validator.apply(e, "maritalStatus", "MARRIED");
        assertThat(e.getMaritalStatus()).isEqualTo(MaritalStatus.MARRIED);
    }

    @Test
    void applyTrimsAndCoercesBlankToNull() {
        Employee e = new Employee();
        e.setPhone("+994 55 000 1111");
        validator.apply(e, "phone", "   ");
        assertThat(e.getPhone()).isNull();
    }

    @Test
    void applyForUnimplementedFieldFailsLoudly() {
        Employee e = new Employee();
        assertThatThrownBy(() -> validator.apply(e, "addressLine1", "123 Main"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not implemented");
    }
}
