package az.millers.hcm.mobility.api;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.mobility.api.dto.InternationalAssignmentRequest;
import az.millers.hcm.mobility.domain.InternationalAssignment;
import az.millers.hcm.mobility.service.MobilityService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobility/assignments")
public class MobilityController {
    private final MobilityService service;
    private final EmployeeContextService employeeContext;
    private final CurrentRequest currentRequest;

    public MobilityController(MobilityService service, EmployeeContextService employeeContext, CurrentRequest currentRequest) {
        this.service = service;
        this.employeeContext = employeeContext;
        this.currentRequest = currentRequest;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<InternationalAssignment> listAssignments(@RequestParam(required = false) String status) {
        return service.listAssignments(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public InternationalAssignment getAssignment(@PathVariable UUID id) {
        return service.getAssignment(id);
    }

    @GetMapping("/my/{employeeId}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<InternationalAssignment> listMyAssignments(@PathVariable UUID employeeId) {
        // IDOR guard: self-or-HR only
        boolean isSelf = employeeContext.currentEmployee().getId().equals(employeeId);
        boolean isHR = currentRequest.hasRole(SecurityRoles.R_SYSTEM_ADMIN) ||
                       currentRequest.hasRole(SecurityRoles.R_HR_ADMIN) ||
                       currentRequest.hasRole(SecurityRoles.R_HR_SPECIALIST);
        if (!isSelf && !isHR) {
            throw new ResourceNotFoundException("Assignments not found");
        }
        return service.listMyAssignments(employeeId);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public InternationalAssignment createAssignment(@Valid @RequestBody InternationalAssignmentRequest req) {
        InternationalAssignment assignment = new InternationalAssignment();
        assignment.setEmployeeId(req.employeeId());
        assignment.setHostCountry(req.hostCountry());
        assignment.setHostCity(req.hostCity());
        assignment.setHostEntity(req.hostEntity());
        assignment.setPurpose(req.purpose());
        assignment.setStartDate(req.startDate());
        assignment.setEndDate(req.endDate());
        assignment.setStatus(req.status() != null ? req.status() : "PLANNED");
        assignment.setVisaType(req.visaType());
        assignment.setVisaExpiry(req.visaExpiry());
        assignment.setHousingAllowance(req.housingAllowance());
        assignment.setColaAmount(req.colaAmount());
        assignment.setHardshipAmount(req.hardshipAmount());
        assignment.setNotes(req.notes());
        return service.createAssignment(assignment);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public InternationalAssignment updateAssignment(@PathVariable UUID id, @Valid @RequestBody InternationalAssignmentRequest req) {
        InternationalAssignment updated = new InternationalAssignment();
        updated.setHostCountry(req.hostCountry());
        updated.setHostCity(req.hostCity());
        updated.setHostEntity(req.hostEntity());
        updated.setPurpose(req.purpose());
        updated.setStartDate(req.startDate());
        updated.setEndDate(req.endDate());
        updated.setStatus(req.status() != null ? req.status() : "PLANNED");
        updated.setVisaType(req.visaType());
        updated.setVisaExpiry(req.visaExpiry());
        updated.setHousingAllowance(req.housingAllowance());
        updated.setColaAmount(req.colaAmount());
        updated.setHardshipAmount(req.hardshipAmount());
        updated.setNotes(req.notes());
        return service.updateAssignment(id, updated);
    }

    @GetMapping("/expiring-visas")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<InternationalAssignment> expiringVisas(@RequestParam(defaultValue = "90") int days) {
        return service.expiringVisas(days);
    }
}
