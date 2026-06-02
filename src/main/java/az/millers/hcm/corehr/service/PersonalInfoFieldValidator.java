package az.millers.hcm.corehr.service;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.MaritalStatus;

/**
 * Field-level validation for {@link az.millers.hcm.corehr.domain.PersonalInfoChangeRequest}
 * — both at submit time (sanity-check the incoming value) and at apply time
 * (re-check before mutating the Employee row) (M79 / P2-23/26).
 *
 * <p>Centralising the validation here means the rules can't drift between
 * the submit path and the workflow-callback apply path.
 */
@Component
public class PersonalInfoFieldValidator {

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE =
            Pattern.compile("^\\+?[0-9 ()\\-]{6,32}$");
    private static final Pattern POSTAL = Pattern.compile("^[A-Za-z0-9 \\-]{3,20}$");
    private static final Pattern COUNTRY_ISO = Pattern.compile("^[A-Z]{2}$");

    /** Whitelist of fields self-service can request edits to. */
    public static final Set<String> ALLOWED_FIELDS = Set.of(
            "email", "phone", "addressLine1", "addressLine2", "city",
            "district", "postalCode", "country", "maritalStatus",
            "emergencyContactName", "emergencyContactPhone");

    /**
     * @throws BadRequestException if the field key is unknown or the value
     *                             fails format validation.
     */
    public void validate(String fieldKey, String newValue) {
        if (fieldKey == null || !ALLOWED_FIELDS.contains(fieldKey)) {
            throw new BadRequestException("Field not editable via self-service: " + fieldKey);
        }
        if (newValue == null) return; // null is allowed — clears the field
        String v = newValue.trim();
        switch (fieldKey) {
            case "email" -> {
                if (!v.isEmpty() && !EMAIL.matcher(v).matches()) {
                    throw new BadRequestException("email is not a valid address");
                }
            }
            case "phone", "emergencyContactPhone" -> {
                if (!v.isEmpty() && !PHONE.matcher(v).matches()) {
                    throw new BadRequestException(fieldKey + " must be a valid phone number");
                }
            }
            case "postalCode" -> {
                if (!v.isEmpty() && !POSTAL.matcher(v).matches()) {
                    throw new BadRequestException("postalCode is not a valid format");
                }
            }
            case "country" -> {
                if (!v.isEmpty() && !COUNTRY_ISO.matcher(v).matches()) {
                    throw new BadRequestException("country must be an ISO 3166-1 alpha-2 code");
                }
            }
            case "maritalStatus" -> {
                if (!v.isEmpty()) {
                    try { MaritalStatus.valueOf(v); }
                    catch (IllegalArgumentException ex) {
                        throw new BadRequestException("maritalStatus must be one of "
                                + Set.of(MaritalStatus.values()));
                    }
                }
            }
            // Free-form strings (addressLine1/2, city, district, names) — only
            // a max-length sanity check; full text is allowed.
            default -> {
                if (v.length() > 200) {
                    throw new BadRequestException(fieldKey + " exceeds 200 characters");
                }
            }
        }
    }

    /**
     * Look up the field's current value on the given employee so we can
     * persist it as the "old value" on the change request.
     */
    public String currentValue(Employee e, String fieldKey) {
        Map<String, java.util.function.Function<Employee, String>> reads = Map.of(
                "email", Employee::getEmail,
                "phone", Employee::getPhone,
                "maritalStatus",
                emp -> emp.getMaritalStatus() == null ? null : emp.getMaritalStatus().name()
        );
        var reader = reads.get(fieldKey);
        if (reader != null) return reader.apply(e);
        // Address / emergency-contact fields live on related rows, not the
        // employee aggregate. The submit path captures NULL for these —
        // the apply path is responsible for resolving the right slice.
        return null;
    }

    /**
     * Apply the validated new value to the employee row. Returns {@code true}
     * iff the entity was actually mutated (callers persist on true).
     *
     * <p>Address / emergency-contact fields are intentionally not handled
     * here — they require their own sub-entity routing and are flagged with
     * a {@code BadRequestException} so a future M79.x ships them.
     */
    public boolean apply(Employee e, String fieldKey, String newValue) {
        validate(fieldKey, newValue);
        String v = newValue == null ? null : newValue.trim();
        if (v != null && v.isEmpty()) v = null;
        switch (fieldKey) {
            case "email" -> { e.setEmail(v); return true; }
            case "phone" -> { e.setPhone(v); return true; }
            case "maritalStatus" -> {
                e.setMaritalStatus(v == null ? null : MaritalStatus.valueOf(v));
                return true;
            }
            default -> throw new BadRequestException(
                    "Apply for field '" + fieldKey + "' is not implemented yet — "
                            + "manual HR edit required");
        }
    }
}
