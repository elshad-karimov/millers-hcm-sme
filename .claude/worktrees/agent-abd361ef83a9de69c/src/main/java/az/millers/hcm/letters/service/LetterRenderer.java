package az.millers.hcm.letters.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import az.millers.hcm.corehr.domain.Employee;

/**
 * Tiny {{placeholder}} substitution engine for HR letters (M77 / P2-16).
 *
 * <p>Resolves dotted-path keys against a flat map: {@code employee.firstName},
 * {@code custom.purpose}, {@code today}. Unknown keys render as the literal
 * marker {@code [missing: key]} so missing data is loud, not silent.
 *
 * <p>This is intentionally not a full template engine — Velocity / Freemarker
 * would bring transitive deps and turn templates into a code-execution surface.
 * The fixed dotted-path resolver is enough for the four common letter shapes
 * and is easy to reason about in security review.
 */
@Component
public class LetterRenderer {

    private static final Pattern MARKER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Substitute markers in {@code body} using employee data + caller-supplied
     * custom fields + the current date.
     */
    public String render(String body, Employee employee, Map<String, Object> customFields) {
        if (body == null) return "";
        Map<String, Object> ctx = buildContext(employee, customFields);
        return substitute(body, ctx);
    }

    /**
     * M283 — raw-context variant: the caller supplies the full key set
     * (e.g. {@code offer.salary}, {@code candidate.fullName}) without the
     * employee-shaped prefixing. {@code today} is always available.
     * Used by offer letters, whose subject is a candidate, not an employee.
     */
    public String render(String body, Map<String, Object> context) {
        if (body == null) return "";
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("today", LocalDate.now().format(ISO_DATE));
        if (context != null) ctx.putAll(context);
        return substitute(body, ctx);
    }

    private String substitute(String body, Map<String, Object> ctx) {
        Matcher m = MARKER.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object resolved = ctx.get(key);
            String replacement = resolved == null
                    ? "[missing: " + key + "]"
                    : resolved.toString();
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private Map<String, Object> buildContext(Employee e, Map<String, Object> custom) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("today", LocalDate.now().format(ISO_DATE));
        if (e != null) {
            ctx.put("employee.firstName", nullToDash(e.getFirstName()));
            ctx.put("employee.lastName", nullToDash(e.getLastName()));
            ctx.put("employee.middleName", nullToDash(e.getMiddleName()));
            ctx.put("employee.employeeNo", nullToDash(e.getEmployeeNo()));
            ctx.put("employee.email", nullToDash(e.getEmail()));
            ctx.put("employee.phone", nullToDash(e.getPhone()));
            ctx.put("employee.hireDate",
                    e.getHireDate() == null ? "—" : e.getHireDate().format(ISO_DATE));
            ctx.put("employee.positionTitle", nullToDash(e.getPositionTitle()));
            ctx.put("employee.departmentName", nullToDash(e.getDepartmentName()));
            ctx.put("employee.costCentre", nullToDash(e.getCostCentre()));
            ctx.put("employee.employmentStatus",
                    e.getEmploymentStatus() == null ? "—" : e.getEmploymentStatus().name());
        }
        if (custom != null) {
            custom.forEach((k, v) -> ctx.put("custom." + k,
                    v == null ? "" : v.toString()));
        }
        return ctx;
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
