package az.millers.hcm.preboarding.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import az.millers.hcm.common.BadRequestException;

/**
 * M122 — pure-static normalisation + validation of the candidate's form
 * payload. Phase 1 schema is fixed in code (next phase: HR can configure
 * which sections to require per invite). Kept mockito-free so the
 * contract gets pinned by plain JUnit tests.
 *
 * <p>Expected shape (all sections optional but at least one required):
 * <pre>{@code
 * {
 *   "personalInfo": {
 *     "phone": "...",
 *     "altPhone": "...",
 *     "personalEmail": "...",
 *     "birthDate": "2000-01-31",
 *     "gender": "MALE|FEMALE|OTHER",
 *     "maritalStatus": "...",
 *     "nationality": "...",
 *     "nationalId": "..."
 *   },
 *   "address": {
 *     "line1": "...",
 *     "city": "...",
 *     "postalCode": "...",
 *     "country": "AZ"
 *   },
 *   "emergencyContacts": [ { "name", "relationship", "phone", "altPhone",
 *                            "email", "address", "primary": true } ],
 *   "dependents": [ { "relationshipType", "firstName", "lastName",
 *                     "middleName", "dateOfBirth", "gender", "nationalId",
 *                     "phone", "email" } ]
 * }
 * }</pre>
 */
public final class PreboardingPayload {

    private PreboardingPayload() {}

    /** Validate the payload shape. Throws {@link BadRequestException} on the first problem. */
    public static void validate(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new BadRequestException("Payload is required");
        }
        boolean hasAnySection = false;
        Object personal = payload.get("personalInfo");
        if (personal != null) {
            hasAnySection = true;
            requireMap("personalInfo", personal);
            validatePersonalInfo(asMap(personal));
        }
        Object address = payload.get("address");
        if (address != null) {
            hasAnySection = true;
            requireMap("address", address);
        }
        Object emergencies = payload.get("emergencyContacts");
        if (emergencies != null) {
            requireList("emergencyContacts", emergencies);
            for (Object e : asList(emergencies)) {
                requireMap("emergencyContacts[]", e);
                Map<String, Object> m = asMap(e);
                requireNonBlank("emergencyContacts[].name", m.get("name"));
                requireNonBlank("emergencyContacts[].relationship", m.get("relationship"));
                requireNonBlank("emergencyContacts[].phone", m.get("phone"));
                hasAnySection = true;
            }
        }
        Object dependents = payload.get("dependents");
        if (dependents != null) {
            requireList("dependents", dependents);
            for (Object d : asList(dependents)) {
                requireMap("dependents[]", d);
                Map<String, Object> m = asMap(d);
                requireNonBlank("dependents[].firstName", m.get("firstName"));
                requireNonBlank("dependents[].lastName", m.get("lastName"));
                requireNonBlank("dependents[].relationshipType", m.get("relationshipType"));
                // Optional date — if present must parse.
                Object dob = m.get("dateOfBirth");
                if (dob != null && !dob.toString().isBlank()) parseDate("dependents[].dateOfBirth", dob);
                hasAnySection = true;
            }
        }
        if (!hasAnySection) {
            throw new BadRequestException(
                    "Provide at least one of personalInfo, emergencyContacts or dependents");
        }
    }

    private static void validatePersonalInfo(Map<String, Object> p) {
        Object birth = p.get("birthDate");
        if (birth != null && !birth.toString().isBlank()) {
            LocalDate d = parseDate("personalInfo.birthDate", birth);
            if (d.isAfter(LocalDate.now())) {
                throw new BadRequestException("personalInfo.birthDate cannot be in the future");
            }
            if (d.isBefore(LocalDate.now().minusYears(120))) {
                throw new BadRequestException("personalInfo.birthDate is implausibly old");
            }
        }
        // Email basic shape gate — full RFC validation happens later
        // when EmployeeService writes the value.
        Object email = p.get("personalEmail");
        if (email != null && !email.toString().isBlank() && !email.toString().contains("@")) {
            throw new BadRequestException("personalInfo.personalEmail looks invalid");
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static void requireMap(String field, Object o) {
        if (!(o instanceof Map<?, ?>)) {
            throw new BadRequestException(field + " must be an object");
        }
    }

    private static void requireList(String field, Object o) {
        if (!(o instanceof List<?>)) {
            throw new BadRequestException(field + " must be an array");
        }
    }

    private static void requireNonBlank(String field, Object o) {
        if (o == null || o.toString().isBlank()) {
            throw new BadRequestException(field + " is required");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        return (List<Object>) o;
    }

    private static LocalDate parseDate(String field, Object raw) {
        try {
            return LocalDate.parse(raw.toString().trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException(field + " must be ISO-8601 (yyyy-MM-dd)");
        }
    }
}
