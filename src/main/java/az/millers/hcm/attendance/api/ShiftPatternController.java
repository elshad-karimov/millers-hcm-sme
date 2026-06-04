package az.millers.hcm.attendance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.AssignmentRequest;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.AssignmentResponse;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.EndAssignmentRequest;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.GenerateRosterRequest;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.GenerateRosterResponse;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.PatternRequest;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.PatternResponse;
import az.millers.hcm.attendance.service.ShiftPatternService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * Shift-pattern REST + auto-roster generator (M111).
 *
 * <ul>
 *   <li>Pattern CRUD — HR_ADMIN_ONLY (changes propagate to every assignment).</li>
 *   <li>Reads — HR_PLUS_MANAGERS so team leads can review their team's rotation.</li>
 *   <li>Assignments + generate — HR_WRITE (HR specialists publish team rosters).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/attendance/shift-patterns")
public class ShiftPatternController {

    private final ShiftPatternService service;

    public ShiftPatternController(ShiftPatternService service) {
        this.service = service;
    }

    // ── Patterns ─────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<PatternResponse> listPatterns(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.listPatterns(activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PatternResponse getPattern(@PathVariable UUID id) {
        return service.getPattern(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PatternResponse createPattern(@Valid @RequestBody PatternRequest req) {
        return service.createPattern(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PatternResponse updatePattern(@PathVariable UUID id, @Valid @RequestBody PatternRequest req) {
        return service.updatePattern(id, req);
    }

    // ── Assignments ──────────────────────────────────────────────────────

    @GetMapping("/assignments/employee/{employeeId}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<AssignmentResponse> assignmentsForEmployee(@PathVariable UUID employeeId) {
        return service.assignmentsForEmployee(employeeId);
    }

    @GetMapping("/{id}/assignments")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<AssignmentResponse> assignmentsForPattern(@PathVariable UUID id) {
        return service.assignmentsForPattern(id);
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public AssignmentResponse assign(@Valid @RequestBody AssignmentRequest req) {
        return service.assign(req);
    }

    @PostMapping("/assignments/{id}/end")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public AssignmentResponse end(@PathVariable UUID id,
                                    @Valid @RequestBody EndAssignmentRequest req) {
        return service.endAssignment(id, req);
    }

    // ── Auto-roster ──────────────────────────────────────────────────────

    @PostMapping("/generate-roster")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public GenerateRosterResponse generate(@Valid @RequestBody GenerateRosterRequest req) {
        return service.generateRoster(req);
    }
}
