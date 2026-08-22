package az.millers.hcm.corehr.service;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import az.millers.hcm.admin.KeycloakAdminService;
import az.millers.hcm.corehr.domain.Employee;

/**
 * Gives a new employee a login.
 *
 * <p>Nothing joined the two halves of the system before this: an employee row
 * and a Keycloak user were created separately, by different people, and were
 * related only by {@code employee.username} matching the Keycloak username
 * exactly. Nothing enforced or created that match, so a new hire had an HR
 * record and no way to sign in — which in this edition means no timesheet, and
 * a month that cannot be paid, because payroll only includes employees with an
 * approved timesheet.
 *
 * <h2>What it does not do</h2>
 * It never sets a password. The account is created with Keycloak's
 * UPDATE_PASSWORD required action, so the employee chooses their own the first
 * time they sign in and nobody else ever knows it. Until then the account is
 * linked but cannot authenticate — see {@link #PASSWORD_NOTE}.
 *
 * <h2>Why it never fails the hire</h2>
 * Keycloak is a separate service that can be down, slow, or misconfigured. If
 * creating the login fails, the employee is still created and the failure is
 * logged and reported — losing the HR record because an identity server was
 * unreachable would be a far worse outcome than a hire that needs its login
 * added afterwards.
 */
@Service
public class EmployeeAccountProvisioner {

    private static final Logger log = LoggerFactory.getLogger(EmployeeAccountProvisioner.class);

    /** Said in one place so the API, the UI and the docs cannot drift. */
    public static final String PASSWORD_NOTE =
            "The account has no password yet. The employee sets one at first sign-in;"
                    + " send them a password-reset from Keycloak to get them started.";

    private final KeycloakAdminService keycloak;

    /**
     * Off by default. Creating accounts in an identity server is not something
     * to start doing to an existing deployment because a new version shipped —
     * an operator turns it on when the realm and the mail setup are ready.
     */
    private final boolean enabled;

    public EmployeeAccountProvisioner(
            KeycloakAdminService keycloak,
            @Value("${hcm.employee.auto-create-login:false}") boolean enabled) {
        this.keycloak = keycloak;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Creates the login and returns the username to store on the employee, or
     * null when nothing was created (switched off, or the attempt failed).
     */
    public String provision(Employee employee) {
        if (!enabled) return null;

        String username = usernameFor(employee);
        if (username == null) {
            log.warn("No login created for {} — no work email, personal email or employee number"
                    + " to derive a username from", employee.getId());
            return null;
        }
        try {
            keycloak.createUser(
                    username,
                    firstNonBlank(employee.getWorkEmail(), employee.getEmail()),
                    employee.getFirstName(),
                    employee.getLastName(),
                    "EMPLOYEE");
            return username;
        } catch (RuntimeException ex) {
            // Deliberately swallowed — see the class comment.
            log.error("Could not create the login for {} ({}). The employee was still created;"
                    + " add the account by hand.", employee.getEmployeeNo(), username, ex);
            return null;
        }
    }

    /**
     * Work email, then personal email, then the employee number.
     *
     * <p>Email first because that is what people can remember and what the
     * password-reset mail needs anyway. The employee number is the fallback
     * that always exists, so a hire with no email address still gets an
     * account rather than being silently skipped.
     */
    public static String usernameFor(Employee employee) {
        String email = firstNonBlank(employee.getWorkEmail(), employee.getEmail());
        if (email != null) return email.trim().toLowerCase(Locale.ROOT);
        if (employee.getEmployeeNo() != null && !employee.getEmployeeNo().isBlank()) {
            return employee.getEmployeeNo().trim().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
