package az.millers.hcm.attendance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.attendance.api.dto.DeviceDtos.DeviceRequest;
import az.millers.hcm.attendance.api.dto.DeviceDtos.DeviceResponse;
import az.millers.hcm.attendance.service.DeviceMasterService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;

/**
 * M333: Device master controller.
 */
@RestController
@RequestMapping("/api/attendance/devices")
public class DeviceMasterController {

    private final DeviceMasterService service;
    private final CurrentRequest currentRequest;

    public DeviceMasterController(DeviceMasterService service, CurrentRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<DeviceResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public DeviceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public DeviceResponse create(@RequestBody DeviceRequest request) {
        return service.create(request, currentRequest.username());
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public DeviceResponse update(@PathVariable UUID id, @RequestBody DeviceRequest request) {
        return service.update(id, request, currentRequest.username());
    }

    @DeleteMapping("/{id}/deactivate")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@PathVariable UUID id) {
        service.activate(id);
    }
}
