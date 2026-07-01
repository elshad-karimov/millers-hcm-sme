package az.millers.hcm.compensation.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compensation.api.dto.CompensationExceptionDto;
import az.millers.hcm.compensation.domain.CompensationExceptionStatus;
import az.millers.hcm.compensation.domain.CompensationExceptionType;
import az.millers.hcm.compensation.service.CompensationExceptionService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M362 — Compensation exceptions controller.
 */
@RestController
@RequestMapping("/api/compensation/exceptions")
public class CompensationExceptionController {

    private final CompensationExceptionService service;

    public CompensationExceptionController(CompensationExceptionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public List<CompensationExceptionDto> list(
            @RequestParam(required = false) CompensationExceptionStatus status,
            @RequestParam(required = false) CompensationExceptionType exceptionType) {
        return service.list(status, exceptionType);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public CompensationExceptionDto resolve(@PathVariable UUID id, @RequestBody ResolveRequest req) {
        return CompensationExceptionDto.from(service.resolve(id, req.status(), req.note()));
    }

    public record ResolveRequest(CompensationExceptionStatus status, String note) {
    }
}
