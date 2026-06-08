package az.millers.hcm.corehr.service;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.AddressType;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeAddressRepository;
import az.millers.hcm.corehr.repo.EmployeeEmergencyContactRepository;
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

    private final EmployeeAddressRepository addresses;
    private final EmployeeEmergencyContactRepository emergencyContacts;

    public PersonalInfoFieldValidator(EmployeeAddressRepository addresses,
                                      EmployeeEmergencyContactRepository emergencyContacts) {
        this.addresses = addresses;
        this.emergencyContacts = emergencyContacts;
    }

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

    /** Set of fields that live on the employee's HOME address slice. */
    private static final Set<String> ADDRESS_FIELDS = Set.of(
            "addressLine1", "addressLine2", "city", "district", "postalCode", "country");

    /** Set of fields that live on the primary emergency contact. */
    private static final Set<String> EMERGENCY_FIELDS = Set.of(
            "emergencyContactName", "emergencyContactPhone");

    /**
     * Apply the validated new value to the employee row (or to the relevant
     * sub-entity for address / emergency-contact fields). Returns {@code true}
     * iff at least one entity was mutated (callers persist on true).
     *
     * <p>Address fields patch the current open HOME address in-place. If no
     * HOME address exists yet, the field is silently skipped (returns false).
     * Emergency-contact fields patch the primary contact; if none exists the
     * field is similarly skipped.
     */
    public boolean apply(Employee e, String fieldKey, String newValue) {
        validate(fieldKey, newValue);
        String v = newValue == null ? null : newValue.trim();
        if (v != null && v.isEmpty()) v = null;

        if (ADDRESS_FIELDS.contains(fieldKey)) {
            return applyAddressField(e.getId(), fieldKey, v);
        }
        if (EMERGENCY_FIELDS.contains(fieldKey)) {
            return applyEmergencyContactField(e.getId(), fieldKey, v);
        }

        switch (fieldKey) {
            case "email" -> { e.setEmail(v); return true; }
            case "phone" -> { e.setPhone(v); return true; }
            case "maritalStatus" -> {
                e.setMaritalStatus(v == null ? null : MaritalStatus.valueOf(v));
                return true;
            }
            default -> throw new BadRequestException(
                    "Apply for field '" + fieldKey + "' is not supported");
        }
    }

    private boolean applyAddressField(java.util.UUID employeeId, String fieldKey, String v) {
        var addr = addresses.findOpenForEmployee(employeeId, AddressType.HOME).orElse(null);
        if (addr == null) return false;
        switch (fieldKey) {
            case "addressLine1" -> addr.setAddressLine1(v);
            case "addressLine2" -> addr.setAddressLine2(v);
            case "city"         -> addr.setCity(v);
            case "district"     -> addr.setDistrict(v);
            case "postalCode"   -> addr.setPostalCode(v);
            case "country"      -> addr.setCountry(v);
        }
        addresses.save(addr);
        return true;
    }

    private boolean applyEmergencyContactField(java.util.UUID employeeId,
                                                String fieldKey, String v) {
        var contact = emergencyContacts.findByEmployeeIdAndPrimaryTrue(employeeId).orElse(null);
        if (contact == null) {
            // Fall back to the first contact in priority order.
            var list = emergencyContacts.findByEmployeeIdOrderByPriorityOrderAsc(employeeId);
            if (list.isEmpty()) return false;
            contact = list.get(0);
        }
        switch (fieldKey) {
            case "emergencyContactName"  -> contact.setName(v);
            case "emergencyContactPhone" -> contact.setPhone(v);
        }
        emergencyContacts.save(contact);
        return true;
    }
}
