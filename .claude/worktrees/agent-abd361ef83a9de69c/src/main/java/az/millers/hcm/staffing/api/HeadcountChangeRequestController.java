package az.millers.hcm.staffing.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.HeadcountChangeDtos.HeadcountChangeResponse;
import az.millers.hcm.staffing.api.dto.HeadcountChangeDtos.HeadcountChangeSubmitRequest;
import az.millers.hcm.staffing.service.HeadcountChangeRequestService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/staffing")
public class HeadcountChangeRequestController {

    private final HeadcountChangeRequestService service;

    public HeadcountChangeRequestController(HeadcountChangeRequestService service) {
        this.service = service;
    }

    /** Submit a headcount change request for a position. */
    @PostMapping("/positions/{positionId}/headcount-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public HeadcountChangeResponse submit(@PathVariable UUID positionId,
                                          @Valid @RequestBody HeadcountChangeSubmitRequest req) {
        return HeadcountChangeResponse.from(service.submit(positionId, req));
    }

    /** List all headcount change requests for a position. */
    @GetMapping("/positions/{positionId}/headcount-requests")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<HeadcountChangeResponse> listByPosition(@PathVariable UUID positionId) {
        return service.listByPosition(positionId)
                .stream()
                .map(HeadcountChangeResponse::from)
                .toList();
    }

    /** List all pending headcount change requests (approver queue). */
    @GetMapping("/headcount-requests/pending")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<HeadcountChangeResponse> listPending() {
        return service.listPending()
                .stream()
                .map(HeadcountChangeResponse::from)
                .toList();
    }
}
