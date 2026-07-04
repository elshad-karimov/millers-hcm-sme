package az.millers.hcm.corehr.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.corehr.service.EmployeeQrCodeService;

/**
 * Public (no authentication) endpoint for employee badge QR verification
 * (PRD §8.1 / EmployeeQrCodeService — "a scanner can hit a public verify
 * endpoint").
 *
 * <p>The QR payload is {@code MILLERS-HCM|<employeeId>|<employeeNo>}.
 * A scanner can:
 * <ul>
 *   <li>POST to {@code /api/public/employees/verify} with the raw payload</li>
 *   <li>GET from {@code /api/public/employees/{id}/badge}</li>
 * </ul>
 *
 * <p>Only non-sensitive fields are returned: employee number, full name,
 * position title, department name, employment status.  No salary, national ID,
 * or contact data is ever returned from this endpoint.
 *
 * <p>SecurityConfig permits anonymous access at
 * {@code /api/public/employees/**}.
 */
@RestController
@RequestMapping("/api/public/employees")
public class PublicEmployeeBadgeController {

    private final EmployeeRepository employees;

    public PublicEmployeeBadgeController(EmployeeRepository employees) {
        this.employees = employees;
    }

    /**
     * Decode a raw QR payload and return the badge info.
     *
     * @param payload raw string from the QR scanner (e.g.
     *                {@code MILLERS-HCM|<uuid>|EMP-00042})
     */
    @GetMapping("/verify")
    public BadgeInfo verify(@RequestParam String payload) {
        String[] parts = payload.split("\\|", -1);
        if (parts.length < 2 || !"MILLERS-HCM".equals(parts[0])) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unrecognised QR payload format");
        }
        UUID id;
        try {
            id = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "QR payload contains an invalid employee ID");
        }
        return badgeFor(id);
    }

    /** Direct lookup by employee UUID — useful for badge apps that already parsed the QR. */
    @GetMapping("/{id}/badge")
    public BadgeInfo badge(@PathVariable UUID id) {
        return badgeFor(id);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private BadgeInfo badgeFor(UUID id) {
        Employee e = employees.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee not found"));
        return new BadgeInfo(
                e.getEmployeeNo(),
                e.getFirstName() + (e.getMiddleName() != null ? " " + e.getMiddleName() : "")
                        + " " + e.getLastName(),
                e.getPositionTitle(),
                e.getDepartmentName(),
                e.getEmploymentStatus().name());
    }

    /** Non-sensitive badge fields returned to anonymous scanners. */
    public record BadgeInfo(
            String employeeNo,
            String fullName,
            String positionTitle,
            String departmentName,
            String employmentStatus) {}
}
