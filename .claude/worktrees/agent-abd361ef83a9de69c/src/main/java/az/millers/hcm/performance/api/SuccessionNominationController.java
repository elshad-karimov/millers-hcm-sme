package az.millers.hcm.performance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.performance.api.dto.SuccessionNominationDtos.CancelRequest;
import az.millers.hcm.performance.api.dto.SuccessionNominationDtos.NominationRequest;
import az.millers.hcm.performance.api.dto.SuccessionNominationDtos.NominationResponse;
import az.millers.hcm.performance.service.SuccessionNominationService;
import az.millers.hcm.security.SecurityRoles;

/**
 * Named-successor nominations (M103). HR-admin-only write; HR read-only.
 * Succession decisions are executive-level — deliberately NOT exposed
 * to department managers.
 */
@RestController
@RequestMapping("/api/performance/succession/nominations")
public class SuccessionNominationController {

    private static final String READ  = SecurityRoles.READ_HR;
    private static final String WRITE = SecurityRoles.WRITE_HR_ADMIN_ONLY;

    private final SuccessionNominationService service;

    public SuccessionNominationController(SuccessionNominationService service) {
        this.service = service;
    }

    @PostMapping("/positions/{positionId}")
    @PreAuthorize(WRITE)
    public NominationResponse nominate(@PathVariable UUID positionId,
                                        @RequestBody NominationRequest req) {
        return service.nominate(positionId, req);
    }

    @DeleteMapping("/{nominationId}")
    @PreAuthorize(WRITE)
    public NominationResponse cancel(@PathVariable UUID nominationId,
                                      @RequestBody(required = false) CancelRequest req) {
        return service.cancel(nominationId, req);
    }

    @GetMapping("/positions/{positionId}")
    @PreAuthorize(READ)
    public List<NominationResponse> forPosition(@PathVariable UUID positionId) {
        return service.forPosition(positionId);
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize(READ)
    public List<NominationResponse> forNominee(@PathVariable UUID employeeId) {
        return service.forNominee(employeeId);
    }
}
