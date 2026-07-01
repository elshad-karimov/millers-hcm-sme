package az.millers.hcm.compensation.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compensation.api.dto.PayBandRequest;
import az.millers.hcm.compensation.api.dto.PayBandResponse;
import az.millers.hcm.compensation.service.PayBandService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M359 — Pay band CRUD endpoints.
 */
@RestController
@RequestMapping("/api/compensation/pay-bands")
public class PayBandController {

    private final PayBandService service;

    public PayBandController(PayBandService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public List<PayBandResponse> list() {
        return service.listActive().stream()
                .map(PayBandResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public PayBandResponse get(@PathVariable UUID id) {
        return PayBandResponse.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    @ResponseStatus(HttpStatus.CREATED)
    public PayBandResponse create(@RequestBody PayBandRequest req) {
        return PayBandResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public PayBandResponse update(@PathVariable UUID id, @RequestBody PayBandRequest req) {
        return PayBandResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
