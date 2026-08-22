package az.millers.hcm.corehr.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import az.millers.hcm.admin.KeycloakAdminService;
import az.millers.hcm.corehr.domain.Employee;

/**
 * An employee marked TERMINATED whose login still works keeps their timesheet,
 * their payslips and their colleagues' data for as long as nobody notices. The
 * formal termination flow revoked access; changing the status directly on the
 * employee screen did not, and that is the route people take.
 */
class EmployeeAccessOnStatusChangeTest {

    private final KeycloakAdminService keycloak = mock(KeycloakAdminService.class);
    private final EmployeeAccountProvisioner provisioner =
            new EmployeeAccountProvisioner(keycloak, true);

    @Test
    @DisplayName("revoking access disables the account")
    void revokeDisables() {
        provisioner.setLoginEnabled(employee("abbas.abbasli@saipem.com"), false);
        verify(keycloak).setUserEnabled("abbas.abbasli@saipem.com", false);
    }

    @Test
    @DisplayName("restoring access enables it again — a rehire must not stay locked out")
    void restoreEnables() {
        provisioner.setLoginEnabled(employee("abbas.abbasli@saipem.com"), true);
        verify(keycloak).setUserEnabled("abbas.abbasli@saipem.com", true);
    }

    @Test
    @DisplayName("an employee with no login is skipped, not guessed at")
    void noLoginNothingToDo() {
        provisioner.setLoginEnabled(employee(null), false);
        verify(keycloak, never()).setUserEnabled(any(), eq(false));
    }

    @Test
    @DisplayName("a Keycloak outage does not block recording the termination")
    void outageDoesNotBlockTheStatusChange() {
        doThrow(new RuntimeException("connection refused"))
                .when(keycloak).setUserEnabled(any(), eq(false));

        // The record must still be correctable even when the door cannot be
        // closed; the error is logged so somebody closes it by hand.
        assertThatCode(() -> provisioner.setLoginEnabled(employee("a@b.com"), false))
                .doesNotThrowAnyException();
    }

    /**
     * The switch is off by default, but revoking access must not depend on it:
     * accounts created before it was turned on still need closing.
     */
    @Test
    @DisplayName("revocation works even when auto-provisioning is switched off")
    void revocationIgnoresTheProvisioningSwitch() {
        KeycloakAdminService kc = mock(KeycloakAdminService.class);
        new EmployeeAccountProvisioner(kc, false)
                .setLoginEnabled(employee("a@b.com"), false);
        verify(kc).setUserEnabled("a@b.com", false);
    }

    private static Employee employee(String username) {
        Employee e = new Employee();
        e.setEmployeeNo("EMP-00002");
        e.setUsername(username);
        return e;
    }
}
