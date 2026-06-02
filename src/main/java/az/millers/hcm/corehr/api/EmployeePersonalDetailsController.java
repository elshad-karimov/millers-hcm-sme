package az.millers.hcm.corehr.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.api.dto.AddressRequest;
import az.millers.hcm.corehr.api.dto.AddressResponse;
import az.millers.hcm.corehr.api.dto.EmergencyContactRequest;
import az.millers.hcm.corehr.api.dto.EmergencyContactResponse;
import az.millers.hcm.corehr.api.dto.IdentificationRequest;
import az.millers.hcm.corehr.api.dto.IdentificationResponse;
import az.millers.hcm.corehr.domain.VerificationStatus;
import az.millers.hcm.corehr.service.EmployeeAddressService;
import az.millers.hcm.corehr.service.EmployeeEmergencyContactService;
import az.millers.hcm.corehr.service.EmployeeIdentificationService;

/**
 * REST surface for the three sub-entities that hang off an employee (M63):
 * identification documents, addresses, emergency contacts.
 *
 * <p>One controller, three URL prefixes — they're all "personal details on
 * an employee" and share an identical role gate, so a single controller
 * keeps the security configuration close to the routes.
 *
 * <ul>
 *   <li>Reads — HR_ADMIN / HR_SPECIALIST / DEPARTMENT_MANAGER (scoped) /
 *       SYSTEM_ADMIN / AUDITOR. Plain document-number is masked at the
 *       service level for everyone except SYSTEM_ADMIN / HR_ADMIN / AUDITOR.</li>
 *   <li>Writes — HR_ADMIN / HR_SPECIALIST / SYSTEM_ADMIN. Department managers
 *       and employees cannot mutate; the upcoming self-service write-back path
 *       (M70+) will go via approval workflows, not these endpoints.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/employees/{employeeId}")
public class EmployeePersonalDetailsController {

    private static final String READ_ROLES =
            "hasAnyRole('HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER','SYSTEM_ADMIN','AUDITOR')";
    private static final String WRITE_ROLES =
            "hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')";

    private final EmployeeIdentificationService identifications;
    private final EmployeeAddressService addresses;
    private final EmployeeEmergencyContactService emergencyContacts;

    public EmployeePersonalDetailsController(EmployeeIdentificationService identifications,
                                              EmployeeAddressService addresses,
                                              EmployeeEmergencyContactService emergencyContacts) {
        this.identifications = identifications;
        this.addresses = addresses;
        this.emergencyContacts = emergencyContacts;
    }

    // ── Identification documents ──────────────────────────────────────────────

    @GetMapping("/identifications")
    @PreAuthorize(READ_ROLES)
    public List<IdentificationResponse> listIdentifications(@PathVariable UUID employeeId) {
        return identifications.listFor(employeeId);
    }

    @PostMapping("/identifications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public IdentificationResponse createIdentification(@PathVariable UUID employeeId,
                                                       @RequestBody @Valid IdentificationRequest req) {
        return identifications.create(employeeId, req);
    }

    @PutMapping("/identifications/{idId}")
    @PreAuthorize(WRITE_ROLES)
    public IdentificationResponse updateIdentification(@PathVariable UUID employeeId,
                                                       @PathVariable UUID idId,
                                                       @RequestBody @Valid IdentificationRequest req) {
        return identifications.update(idId, req);
    }

    @PostMapping("/identifications/{idId}/verify")
    @PreAuthorize(WRITE_ROLES)
    public IdentificationResponse verifyIdentification(@PathVariable UUID employeeId,
                                                       @PathVariable UUID idId,
                                                       @RequestParam VerificationStatus status) {
        return identifications.verify(idId, status);
    }

    @DeleteMapping("/identifications/{idId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void deleteIdentification(@PathVariable UUID employeeId, @PathVariable UUID idId) {
        identifications.delete(idId);
    }

    // ── Addresses ─────────────────────────────────────────────────────────────

    @GetMapping("/addresses")
    @PreAuthorize(READ_ROLES)
    public List<AddressResponse> listAddresses(@PathVariable UUID employeeId,
                                                @RequestParam(required = false, defaultValue = "false")
                                                boolean currentOnly) {
        return currentOnly
                ? addresses.currentFor(employeeId)
                : addresses.listFor(employeeId);
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public AddressResponse createAddress(@PathVariable UUID employeeId,
                                          @RequestBody @Valid AddressRequest req) {
        return addresses.create(employeeId, req);
    }

    @PutMapping("/addresses/{addressId}")
    @PreAuthorize(WRITE_ROLES)
    public AddressResponse updateAddress(@PathVariable UUID employeeId,
                                          @PathVariable UUID addressId,
                                          @RequestBody @Valid AddressRequest req) {
        return addresses.update(addressId, req);
    }

    @DeleteMapping("/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void deleteAddress(@PathVariable UUID employeeId, @PathVariable UUID addressId) {
        addresses.delete(addressId);
    }

    // ── Emergency contacts ────────────────────────────────────────────────────

    @GetMapping("/emergency-contacts")
    @PreAuthorize(READ_ROLES)
    public List<EmergencyContactResponse> listEmergencyContacts(@PathVariable UUID employeeId) {
        return emergencyContacts.listFor(employeeId);
    }

    @PostMapping("/emergency-contacts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public EmergencyContactResponse createEmergencyContact(
            @PathVariable UUID employeeId,
            @RequestBody @Valid EmergencyContactRequest req) {
        return emergencyContacts.create(employeeId, req);
    }

    @PutMapping("/emergency-contacts/{contactId}")
    @PreAuthorize(WRITE_ROLES)
    public EmergencyContactResponse updateEmergencyContact(
            @PathVariable UUID employeeId,
            @PathVariable UUID contactId,
            @RequestBody @Valid EmergencyContactRequest req) {
        return emergencyContacts.update(contactId, req);
    }

    @DeleteMapping("/emergency-contacts/{contactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void deleteEmergencyContact(@PathVariable UUID employeeId, @PathVariable UUID contactId) {
        emergencyContacts.delete(contactId);
    }
}
