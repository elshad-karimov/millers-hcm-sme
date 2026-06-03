package az.millers.hcm.corehr.api;

import az.millers.hcm.security.SecurityRoles;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.corehr.api.dto.PersonalInfoChangeResponse;
import az.millers.hcm.corehr.domain.PersonalInfoChangeStatus;
import az.millers.hcm.corehr.service.PersonalInfoChangeService;

@RestController
@RequestMapping("/api/personal-info-changes")
public class PersonalInfoChangeController {

    private final PersonalInfoChangeService service;

    public PersonalInfoChangeController(PersonalInfoChangeService service) {
        this.service = service;
    }

    /** HR / scoped-manager queue. Scope filter is applied inside the service. */
    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PageResponse<PersonalInfoChangeResponse> list(
            @RequestParam(required = false) PersonalInfoChangeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("submittedAt").descending());
        return PageResponse.of(service.list(status, pageable), PersonalInfoChangeResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public PersonalInfoChangeResponse get(@PathVariable UUID id) {
        return PersonalInfoChangeResponse.from(service.get(id));
    }
}
