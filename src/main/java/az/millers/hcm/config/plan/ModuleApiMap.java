package az.millers.hcm.config.plan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a request path to the {@link HcmModule} that owns it.
 *
 * <p><b>Longest prefix wins</b>, so nested ownership works:
 * {@code /api/reports/payroll} belongs to PAYROLL while plain {@code /api/reports}
 * belongs to REPORTS_ANALYTICS.
 *
 * <p><b>Segment-aware</b>: a prefix matches only on a path-segment boundary, so
 * {@code /api/positions} never swallows {@code /api/position-occupancies}.
 *
 * <p>A path owned by nobody resolves to {@link Optional#empty()} and is left
 * ungated — see the class note on {@link HcmModule}.
 */
public final class ModuleApiMap {

    /** prefix -> owning module. Insertion order is irrelevant; length decides. */
    private static final Map<String, HcmModule> PREFIXES = buildPrefixIndex();

    private ModuleApiMap() {}

    private static Map<String, HcmModule> buildPrefixIndex() {
        Map<String, HcmModule> index = new LinkedHashMap<>();
        for (HcmModule module : HcmModule.values()) {
            for (String prefix : module.apiPrefixes()) {
                HcmModule clash = index.putIfAbsent(prefix, module);
                if (clash != null) {
                    // Two modules claiming one prefix is a programming error: the
                    // gate would be ambiguous. Fail at class-init, not in prod.
                    throw new IllegalStateException(
                            "API prefix '" + prefix + "' claimed by both "
                                    + clash + " and " + module);
                }
            }
        }
        return Map.copyOf(index);
    }

    /**
     * The module owning {@code path}, or empty when the path is shared /
     * infrastructure and must not be gated.
     *
     * @param path servlet path, e.g. {@code /api/leave/requests/42}
     */
    public static Optional<HcmModule> resolve(String path) {
        if (path == null || path.isEmpty()) {
            return Optional.empty();
        }
        String normalised = stripTrailingSlash(path);
        HcmModule best = null;
        int bestLength = -1;
        for (Map.Entry<String, HcmModule> e : PREFIXES.entrySet()) {
            String prefix = e.getKey();
            if (matches(normalised, prefix) && prefix.length() > bestLength) {
                best = e.getValue();
                bestLength = prefix.length();
            }
        }
        return Optional.ofNullable(best);
    }

    /** Segment-boundary prefix match: exact, or followed by '/'. */
    private static boolean matches(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static String stripTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;
    }

    /** All registered prefixes — used by the coverage test. */
    static Map<String, HcmModule> prefixes() {
        return PREFIXES;
    }
}
