package az.millers.hcm.staffing.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.PositionTransferDtos.ActionRequest;
import az.millers.hcm.staffing.api.dto.PositionTransferDtos.InitiateRequest;
import az.millers.hcm.staffing.api.dto.PositionTransferDtos.TransferResponse;
import az.millers.hcm.staffing.service.PositionTransferService;

/** M260 — REST surface for position transfer workflow (PRD §40). */
@RestController
@RequestMapping("/api/positions/transfers")
public class PositionTransferController {

    private final PositionTransferService service;

    public PositionTransferController(PositionTransferService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<TransferResponse> list(@RequestParam UUID positionId) {
        return service.listForPosition(positionId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public TransferResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TransferResponse initiate(@Valid @RequestBody InitiateRequest req) {
        return service.initiate(req);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TransferResponse submit(@PathVariable UUID id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TransferResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TransferResponse reject(@PathVariable UUID id,
                                    @RequestBody(required = false) ActionRequest req) {
        return service.reject(id, req);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TransferResponse complete(@PathVariable UUID id) {
        return service.complete(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TransferResponse cancel(@PathVariable UUID id,
                                    @RequestBody(required = false) ActionRequest req) {
        return service.cancel(id, req);
    }
}
