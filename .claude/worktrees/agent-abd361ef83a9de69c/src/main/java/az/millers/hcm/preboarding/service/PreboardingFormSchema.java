package az.millers.hcm.preboarding.service;

import java.util.List;
import java.util.Map;

/**
 * M122 — Phase 1 form schema descriptor returned to the candidate SPA
 * from {@code GET /api/public/preboarding/{token}/info}. The SPA reads
 * this to render the multi-step form; the schema is hard-coded for
 * Phase 1 (Phase 2: HR can configure which sections to require per
 * invite).
 *
 * <p>Kept as a Java structure (rather than a JSON resource file) so the
 * field keys can be cross-checked at compile time against
 * {@link PreboardingPayload}.
 */
public final class PreboardingFormSchema {

    private PreboardingFormSchema() {}

    public static final Map<String, Object> SCHEMA = Map.of(
        "version", 1,
        "sections", List.of(
            Map.of(
                "key", "personalInfo",
                "title", "Personal information",
                "fields", List.of(
                    field("phone", "Phone", "string", true),
                    field("altPhone", "Alternate phone", "string", false),
                    field("personalEmail", "Personal email", "email", false),
                    field("birthDate", "Date of birth", "date", false),
                    enumField("gender", "Gender", false, List.of("MALE", "FEMALE", "OTHER")),
                    enumField("maritalStatus", "Marital status", false,
                            List.of("SINGLE", "MARRIED", "DIVORCED", "WIDOWED", "OTHER")),
                    field("nationality", "Nationality", "string", false),
                    field("nationalId", "National ID", "string", false))),
            Map.of(
                "key", "emergencyContacts",
                "title", "Emergency contacts",
                "multiple", true,
                "fields", List.of(
                    field("name", "Name", "string", true),
                    enumField("relationship", "Relationship", true,
                            List.of("SPOUSE", "CHILD", "PARENT", "SIBLING", "GUARDIAN", "FRIEND", "OTHER")),
                    field("phone", "Phone", "string", true),
                    field("altPhone", "Alt phone", "string", false),
                    field("email", "Email", "email", false),
                    field("address", "Address", "text", false),
                    field("primary", "Primary contact", "boolean", false))),
            Map.of(
                "key", "dependents",
                "title", "Dependents",
                "multiple", true,
                "fields", List.of(
                    enumField("relationshipType", "Relationship", true,
                            List.of("SPOUSE", "CHILD", "PARENT", "OTHER")),
                    field("firstName", "First name", "string", true),
                    field("lastName", "Last name", "string", true),
                    field("middleName", "Middle name", "string", false),
                    field("dateOfBirth", "Date of birth", "date", false),
                    enumField("gender", "Gender", false, List.of("MALE", "FEMALE", "OTHER")),
                    field("nationalId", "National ID", "string", false),
                    field("phone", "Phone", "string", false),
                    field("email", "Email", "email", false))))
    );

    private static Map<String, Object> field(String key, String label, String type, boolean required) {
        return Map.of("key", key, "label", label, "type", type, "required", required);
    }

    private static Map<String, Object> enumField(String key, String label, boolean required, List<String> options) {
        return Map.of(
                "key", key,
                "label", label,
                "type", "enum",
                "required", required,
                "options", options);
    }
}
