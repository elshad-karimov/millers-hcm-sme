package az.millers.hcm.lifecycle.api;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import az.millers.hcm.lifecycle.domain.AssetTransfer;
import az.millers.hcm.lifecycle.service.AssetTransferService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M457 — Asset transfer requests (HR approve/complete).
 */
@RestController
@RequestMapping("/api/assets/transfers")
public class AssetTransferController {

    private final AssetTransferService service;

    public AssetTransferController(AssetTransferService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<AssetTransfer> listAll() {
        return service.listAll();
    }

    @GetMapping("/pending")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<AssetTransfer> listPending() {
        return service.listPending();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public AssetTransfer get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public AssetTransfer request(@RequestBody TransferRequest req) {
        return service.request(req.assetId, req.fromEmployeeId, req.toEmployeeId, req.reason);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AssetTransfer approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AssetTransfer complete(@PathVariable UUID id) {
        return service.complete(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AssetTransfer reject(@PathVariable UUID id, @RequestBody RejectRequest req) {
        return service.reject(id, req.reason);
    }

    public record TransferRequest(UUID assetId, UUID fromEmployeeId, UUID toEmployeeId, String reason) {}
    public record RejectRequest(String reason) {}
}
