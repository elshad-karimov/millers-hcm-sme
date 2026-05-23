package az.millers.hcm.compbenefits.api;

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

import az.millers.hcm.compbenefits.api.dto.AllowanceTypeRequest;
import az.millers.hcm.compbenefits.api.dto.AllowanceTypeResponse;
import az.millers.hcm.compbenefits.service.AllowanceTypeService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/compbenefits/allowance-types")
public class AllowanceTypeController {

    private final AllowanceTypeService service;

    public AllowanceTypeController(AllowanceTypeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<AllowanceTypeResponse> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return service.list(activeOnly).stream().map(AllowanceTypeResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public AllowanceTypeResponse get(@PathVariable UUID id) {
        return AllowanceTypeResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public AllowanceTypeResponse create(@Valid @RequestBody AllowanceTypeRequest req) {
        return AllowanceTypeResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public AllowanceTypeResponse update(@PathVariable UUID id,
                                          @Valid @RequestBody AllowanceTypeRequest req) {
        return AllowanceTypeResponse.from(service.update(id, req));
    }
}
