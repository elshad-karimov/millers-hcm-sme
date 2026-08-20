package az.millers.hcm.payroll.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import az.millers.hcm.payroll.profile.api.CalculationProfileAdminController;

/**
 * Salary and payroll amounts are permission-controlled (global rules 6, 8, 9).
 *
 * <p>This repository has no web test infrastructure, so rather than stand up a
 * brittle one these tests read the authorization annotations directly. That is
 * narrower than a request-level test — it proves the rules are declared, not
 * that Spring enforces them — but it catches the regression that actually
 * happens: someone adds an endpoint and forgets the guard, or widens the role
 * list to make their own testing easier.
 *
 * <p>The employee and manager roles are asserted absent explicitly. The whole
 * point of the timesheet slices is that the people who record and approve time
 * see quantities; only payroll sees what those quantities are worth.
 */
class ProfileEndpointSecurityTest {

    /** Roles that may see or change pay. */
    private static final Set<String> PAYROLL_ROLES = Set.of(
            "PAYROLL_SPECIALIST", "COMPENSATION_MANAGER", "HR_ADMIN", "SYSTEM_ADMIN");

    /** Roles that must never appear on any endpoint in this feature. */
    private static final Set<String> FORBIDDEN_ROLES = Set.of(
            "EMPLOYEE", "DEPARTMENT_MANAGER", "HR_SPECIALIST");

    private static final List<Class<?>> CONTROLLERS = List.of(
            ProfilePayPreviewController.class,
            CalculationProfileAdminController.class);

    private static List<Method> endpoints(Class<?> controller) {
        List<Method> out = new ArrayList<>();
        for (Method m : controller.getDeclaredMethods()) {
            if (m.isSynthetic() || !java.lang.reflect.Modifier.isPublic(m.getModifiers())) continue;
            if (m.isAnnotationPresent(GetMapping.class)
                    || m.isAnnotationPresent(PostMapping.class)
                    || m.isAnnotationPresent(PutMapping.class)
                    || m.isAnnotationPresent(DeleteMapping.class)
                    || m.isAnnotationPresent(PatchMapping.class)
                    || m.isAnnotationPresent(RequestMapping.class)) {
                out.add(m);
            }
        }
        return out;
    }

    private static String guardFor(Class<?> controller, Method endpoint) {
        PreAuthorize onMethod = endpoint.getAnnotation(PreAuthorize.class);
        if (onMethod != null) return onMethod.value();
        PreAuthorize onClass = controller.getAnnotation(PreAuthorize.class);
        return onClass == null ? null : onClass.value();
    }

    @Test
    @DisplayName("every endpoint in the feature is behind an authorization guard")
    void everyEndpointIsGuarded() {
        List<String> unguarded = new ArrayList<>();
        for (Class<?> c : CONTROLLERS) {
            for (Method m : endpoints(c)) {
                if (guardFor(c, m) == null) {
                    unguarded.add(c.getSimpleName() + "." + m.getName());
                }
            }
        }
        assertThat(unguarded)
                .as("endpoints with no @PreAuthorize — these would expose salary data")
                .isEmpty();
    }

    @Test
    @DisplayName("no endpoint is reachable by an employee, a manager or an HR specialist")
    void noEndpointIsReachableByNonPayrollRoles() {
        List<String> leaks = new ArrayList<>();
        for (Class<?> c : CONTROLLERS) {
            for (Method m : endpoints(c)) {
                String guard = guardFor(c, m);
                for (String role : FORBIDDEN_ROLES) {
                    if (guard != null && guard.contains("'" + role + "'")) {
                        leaks.add(c.getSimpleName() + "." + m.getName() + " allows " + role);
                    }
                }
            }
        }
        assertThat(leaks)
                .as("managers and employees record and approve time; they never see amounts")
                .isEmpty();
    }

    @Test
    @DisplayName("only payroll roles are named, so a new role cannot be added by accident")
    void onlyPayrollRolesAreNamed() {
        for (Class<?> c : CONTROLLERS) {
            for (Method m : endpoints(c)) {
                String guard = guardFor(c, m);
                assertThat(guard).isNotNull();
                for (String role : rolesIn(guard)) {
                    assertThat(PAYROLL_ROLES)
                            .as("%s.%s names role %s", c.getSimpleName(), m.getName(), role)
                            .contains(role);
                }
            }
        }
    }

    @Test
    @DisplayName("writing configuration is narrower than reading it — no HR_ADMIN")
    void adminIsNarrowerThanPreview() {
        String admin = CalculationProfileAdminController.class
                .getAnnotation(PreAuthorize.class).value();
        String preview = ProfilePayPreviewController.class
                .getAnnotation(PreAuthorize.class).value();

        // Reading a preview is a check; changing a multiplier moves money.
        assertThat(rolesIn(admin)).doesNotContain("HR_ADMIN");
        assertThat(rolesIn(preview)).contains("HR_ADMIN");
        assertThat(rolesIn(admin)).isSubsetOf(rolesIn(preview));
    }

    @Test
    @DisplayName("the preview controller exposes reads only — nothing there writes")
    void previewIsReadOnly() {
        for (Method m : endpoints(ProfilePayPreviewController.class)) {
            assertThat(m.isAnnotationPresent(GetMapping.class))
                    .as("%s is not a GET; the preview must not create or change anything",
                            m.getName())
                    .isTrue();
        }
    }

    private static List<String> rolesIn(String guard) {
        List<String> roles = new ArrayList<>();
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("'([A-Z_]+)'").matcher(guard);
        while (matcher.find()) {
            roles.add(matcher.group(1));
        }
        return roles;
    }
}
