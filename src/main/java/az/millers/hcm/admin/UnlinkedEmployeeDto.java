package az.millers.hcm.admin;

import java.util.UUID;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.service.EmployeeAccountProvisioner;

/**
 * An employee who has no sign-in account.
 *
 * <p>User Management lists Keycloak logins, so an employee without one was
 * simply absent — and absent looks identical to "already handled". These rows
 * make the gap visible, because an employee who cannot sign in cannot file a
 * timesheet, and a month with no approved timesheet is a month payroll will
 * not pay.
 *
 * <p>{@code proposedUsername} is what the account would be called, worked out
 * by the same rule that creates it, so nobody has to guess before clicking.
 */
public record UnlinkedEmployeeDto(
        UUID employeeId,
        String employeeNo,
        String fullName,
        String email,
        String proposedUsername) {

    public static UnlinkedEmployeeDto from(Employee e) {
        String email = e.getWorkEmail() != null && !e.getWorkEmail().isBlank()
                ? e.getWorkEmail()
                : e.getEmail();
        return new UnlinkedEmployeeDto(
                e.getId(),
                e.getEmployeeNo(),
                (e.getLastName() + ", " + e.getFirstName()).trim(),
                email,
                EmployeeAccountProvisioner.usernameFor(e));
    }
}
