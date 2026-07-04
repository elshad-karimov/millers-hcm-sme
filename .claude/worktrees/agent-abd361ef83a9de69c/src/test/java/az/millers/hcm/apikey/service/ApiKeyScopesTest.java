package az.millers.hcm.apikey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.security.SecurityRoles;

/**
 * M120 — scope normalisation. Pins:
 * <ul>
 *   <li>unknown / typo'd scopes are rejected with a useful message,</li>
 *   <li>duplicates collapse, ordering is preserved,</li>
 *   <li>empty / blank-only lists are rejected.</li>
 * </ul>
 */
class ApiKeyScopesTest {

    @Test
    void acceptsKnownScope() {
        assertThat(ApiKeyScopes.normalise(List.of(SecurityRoles.R_PAYROLL_SPECIALIST)))
                .containsExactly(SecurityRoles.R_PAYROLL_SPECIALIST);
    }

    @Test
    void dedupesAndPreservesFirstOccurrenceOrder() {
        List<String> out = ApiKeyScopes.normalise(Arrays.asList(
                SecurityRoles.R_HR_ADMIN,
                SecurityRoles.R_EMPLOYEE,
                SecurityRoles.R_HR_ADMIN));
        assertThat(out).containsExactly(SecurityRoles.R_HR_ADMIN, SecurityRoles.R_EMPLOYEE);
    }

    @Test
    void stripsBlankEntries() {
        List<String> out = ApiKeyScopes.normalise(Arrays.asList(
                "  ", SecurityRoles.R_RECRUITER, ""));
        assertThat(out).containsExactly(SecurityRoles.R_RECRUITER);
    }

    @Test
    void rejectsUnknownScope() {
        assertThatThrownBy(() -> ApiKeyScopes.normalise(List.of("HR_NINJA")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown scope: HR_NINJA");
    }

    @Test
    void rejectsEmptyList() {
        assertThatThrownBy(() -> ApiKeyScopes.normalise(List.of()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsAllBlankList() {
        assertThatThrownBy(() -> ApiKeyScopes.normalise(Arrays.asList(" ", "")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsNullList() {
        assertThatThrownBy(() -> ApiKeyScopes.normalise(null))
                .isInstanceOf(BadRequestException.class);
    }
}
