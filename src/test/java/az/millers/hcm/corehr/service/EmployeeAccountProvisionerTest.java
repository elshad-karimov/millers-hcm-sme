package az.millers.hcm.corehr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import az.millers.hcm.admin.KeycloakAdminService;
import az.millers.hcm.corehr.domain.Employee;

/**
 * A hire with no login cannot fill a timesheet, and a month with no timesheet
 * cannot be paid — so provisioning matters. But it talks to a separate service
 * that can be down, and the one thing it must never do is take the hire with it.
 */
class EmployeeAccountProvisionerTest {

    private final KeycloakAdminService keycloak = mock(KeycloakAdminService.class);

    @Test
    @DisplayName("a Keycloak outage does not lose the employee")
    void keycloakFailureDoesNotFailTheHire() {
        when(keycloak.createUser(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("connection refused"));
        EmployeeAccountProvisioner provisioner = new EmployeeAccountProvisioner(keycloak, true);

        String username = provisioner.provision(employee("EMP-00009", "a.b@saipem.com", null));

        // No exception, and no username to store — the caller keeps the employee.
        assertThat(username).isNull();
    }

    @Test
    @DisplayName("switched off, nothing is created and no call is made")
    void disabledCreatesNothing() {
        EmployeeAccountProvisioner provisioner = new EmployeeAccountProvisioner(keycloak, false);

        assertThat(provisioner.provision(employee("EMP-00009", "a.b@saipem.com", null))).isNull();
        verify(keycloak, never()).createUser(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("the login is the work email, lower-cased, with the EMPLOYEE role")
    void usesWorkEmail() {
        EmployeeAccountProvisioner provisioner = new EmployeeAccountProvisioner(keycloak, true);

        String username = provisioner.provision(
                employee("EMP-00009", "Abbas.Abbasli@Saipem.com", "personal@gmail.com"));

        assertThat(username).isEqualTo("abbas.abbasli@saipem.com");
        verify(keycloak).createUser(eq("abbas.abbasli@saipem.com"), eq("Abbas.Abbasli@Saipem.com"),
                any(), any(), eq("EMPLOYEE"));
    }

    @Test
    @DisplayName("no work email falls back to the personal one")
    void fallsBackToPersonalEmail() {
        assertThat(EmployeeAccountProvisioner.usernameFor(
                employee("EMP-00009", null, "Aliya@gmail.com"))).isEqualTo("aliya@gmail.com");
    }

    @Test
    @DisplayName("no email at all still gets an account, keyed on the employee number")
    void fallsBackToEmployeeNumber() {
        assertThat(EmployeeAccountProvisioner.usernameFor(employee("EMP-00009", null, null)))
                .isEqualTo("emp-00009");
    }

    @Test
    @DisplayName("nothing to derive a username from is skipped, not guessed")
    void nothingToDeriveFrom() {
        Employee blank = new Employee();
        assertThat(EmployeeAccountProvisioner.usernameFor(blank)).isNull();

        EmployeeAccountProvisioner provisioner = new EmployeeAccountProvisioner(keycloak, true);
        assertThatCode(() -> provisioner.provision(blank)).doesNotThrowAnyException();
        verify(keycloak, never()).createUser(any(), any(), any(), any(), any());
    }

    private static Employee employee(String employeeNo, String workEmail, String personalEmail) {
        Employee e = new Employee();
        e.setEmployeeNo(employeeNo);
        e.setFirstName("Abbas");
        e.setLastName("Abbasli");
        e.setWorkEmail(workEmail);
        e.setEmail(personalEmail);
        return e;
    }
}
