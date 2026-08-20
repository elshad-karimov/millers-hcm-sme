package az.millers.hcm.config.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every {@code /api} controller must be classified: owned by a module (gated) or
 * deliberately shared (never gated).
 *
 * <p>{@link ModuleApiMap} fails OPEN — an unmapped path stays reachable — which
 * is the right default (better to under-gate than to brick a lean plan's core
 * screens) but would otherwise let a new premium controller ship ungated in
 * silence. This test removes the silence: add a controller under a new root and
 * the build fails until someone decides which module owns it, or records it as
 * shared below.
 */
class ApiPathCoverageTest {

    private static final String BASE_PACKAGE = "az.millers.hcm";

    /**
     * Roots that are intentionally ungated, with the reason.
     *
     * <p>Mostly reference data and cross-cutting infrastructure read by forms in
     * every module: binding them to one module would break a lean plan's core
     * screens (e.g. the LITE new-hire form needs job functions and grades even
     * though Staffing & Positions is a STANDARD module).
     */
    private static final Set<String> SHARED_UNGATED_ROOTS = Set.of(
            "/api/public",          // anonymous by design (careers, letter verify, preboarding)
            "/api/auth",            // authentication surface
            "/api/attachments",     // generic file attachments, used by every module
            "/api/documents",       // document categories + signatures, cross-module
            "/api/notifications",   // notification prefs + delivery, cross-module
            "/api/holidays",        // public-holiday calendar: leave AND attendance day counting
            "/api/grades",          // reference: pay/job grades on the employee form
            "/api/job-families",    // reference: job taxonomy on the employee form
            "/api/job-functions",   // reference: job taxonomy on the employee form
            "/api/skills"           // reference: skill taxonomy shared by learning/talent/recruitment
    );

    @Test
    @DisplayName("every /api controller is either owned by a module or recorded as shared")
    void everyApiRootIsClassified() {
        var unclassified = new TreeMap<String, String>(); // path -> controller

        for (var mapping : scanApiMappings().entrySet()) {
            String path = mapping.getKey();
            if (ModuleApiMap.resolve(path).isPresent() || isSharedRoot(path)) {
                continue;
            }
            unclassified.put(path, mapping.getValue());
        }

        assertThat(unclassified)
                .as("""
                        Unclassified /api paths — these ship UNGATED, so a premium module \
                        would answer on a LITE plan. Either add the prefix to the owning \
                        HcmModule, or add its root to SHARED_UNGATED_ROOTS with the reason.
                        """)
                .isEmpty();
    }

    @Test
    @DisplayName("the shared list stays honest — no entry that a module already owns")
    void sharedRootsAreNotAlsoOwned() {
        var contradictions = new TreeSet<String>();
        for (String shared : SHARED_UNGATED_ROOTS) {
            ModuleApiMap.resolve(shared).ifPresent(
                    owner -> contradictions.add(shared + " (owned by " + owner.key() + ")"));
        }
        assertThat(contradictions)
                .as("A root cannot be both module-owned and shared — the gate would be ambiguous")
                .isEmpty();
    }

    @Test
    @DisplayName("the scan actually found the controllers it is meant to police")
    void scanIsNotVacuous() {
        // Guards against a silent pass if the package moves or scanning breaks.
        var paths = scanApiMappings().keySet();
        assertThat(paths).hasSizeGreaterThan(100);
        assertThat(paths).anyMatch(p -> p.startsWith("/api/leave/requests"));
        assertThat(paths).anyMatch(p -> p.startsWith("/api/payroll/runs"));
        // A controller whose class prefix is bare /api and whose real paths live
        // on its methods must be seen as those method paths, not as "/api".
        assertThat(paths).doesNotContain("/api");
    }

    private static boolean isSharedRoot(String path) {
        return SHARED_UNGATED_ROOTS.stream()
                .anyMatch(root -> path.equals(root) || path.startsWith(root + "/"));
    }

    /**
     * Every handler path under {@code /api} → its declaring class.
     *
     * <p>Class prefix + method path, because a controller may map {@code /api}
     * at class level and carry the real paths on its methods — and those methods
     * can even span two modules (self-service vs HR operations), which is
     * exactly what the runtime filter resolves per request.
     */
    private static TreeMap<String, String> scanApiMappings() {
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        var mappings = new TreeMap<String, String>();
        for (BeanDefinition definition : provider.findCandidateComponents(BASE_PACKAGE)) {
            String className = definition.getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> type;
            try {
                type = Class.forName(className);
            } catch (ClassNotFoundException | NoClassDefFoundError ex) {
                continue;
            }
            RequestMapping classMapping =
                    AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            if (classMapping == null) {
                continue;
            }
            for (String classPath : pathsOf(classMapping)) {
                for (String full : handlerPaths(type, classPath)) {
                    if (full.startsWith("/api")) {
                        mappings.put(full, type.getSimpleName());
                    }
                }
            }
        }
        return mappings;
    }

    /** Full paths contributed by a controller's handler methods under {@code classPath}. */
    private static Set<String> handlerPaths(Class<?> type, String classPath) {
        var paths = new TreeSet<String>();
        for (var method : type.getDeclaredMethods()) {
            RequestMapping mapping =
                    AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) {
                continue;
            }
            String[] methodPaths = pathsOf(mapping);
            if (methodPaths.length == 0) {
                paths.add(classPath);
                continue;
            }
            for (String methodPath : methodPaths) {
                paths.add(join(classPath, methodPath));
            }
        }
        // A controller with no annotated handlers still asserts its own prefix.
        return paths.isEmpty() ? Set.of(classPath) : paths;
    }

    private static String[] pathsOf(RequestMapping mapping) {
        return mapping.value().length > 0 ? mapping.value() : mapping.path();
    }

    private static String join(String classPath, String methodPath) {
        if (methodPath.isEmpty() || "/".equals(methodPath)) {
            return classPath;
        }
        String base = classPath.endsWith("/")
                ? classPath.substring(0, classPath.length() - 1) : classPath;
        return methodPath.startsWith("/") ? base + methodPath : base + "/" + methodPath;
    }
}
