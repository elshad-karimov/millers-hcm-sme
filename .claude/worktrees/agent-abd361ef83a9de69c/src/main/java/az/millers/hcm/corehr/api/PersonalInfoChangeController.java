package az.millers.hcm.corehr.api;

import az.millers.hcm.security.SecurityRoles;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.corehr.api.dto.PersonalInfoChangeResponse;
import az.millers.hcm.corehr.api.dto.PersonalInfoChangeSubmitRequest;
import az.millers.hcm.corehr.domain.PersonalInfoChangeStatus;
import az.millers.hcm.corehr.service.PersonalInfoChangeService;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/personal-info-changes")
public class PersonalInfoChangeController {

    private final PersonalInfoChangeService service;
    private final EmployeeContextService context;
    private final az.millers.hcm.security.CurrentRequest currentRequest;

    public PersonalInfoChangeController(PersonalInfoChangeService service,
                                         EmployeeContextService context,
                                         az.millers.hcm.security.CurrentRequest currentRequest) {
        this.service = service;
        this.context = context;
        this.currentRequest = currentRequest;
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

    /** Employee submits a personal-info change request for HR approval. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public PersonalInfoChangeResponse submit(@Valid @RequestBody PersonalInfoChangeSubmitRequest req) {
        return PersonalInfoChangeResponse.from(service.submit(req));
    }

    /** Employee's own requests (self-service view). */
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public List<PersonalInfoChangeResponse> mine() {
        UUID empId = context.currentEmployee().getId();
        return service.mine(empId).stream().map(PersonalInfoChangeResponse::from).toList();
    }

    /** M218 — HR can cancel a pending personal-info change request. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public PersonalInfoChangeResponse cancel(@PathVariable UUID id,
                                              @RequestParam(required = false) String reason) {
        return PersonalInfoChangeResponse.from(
                service.onCancelled(id, currentRequest.username(), reason));
    }
}
