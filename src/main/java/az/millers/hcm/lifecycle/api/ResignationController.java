package az.millers.hcm.lifecycle.api;

import java.util.UUID;

import java.time.LocalDate;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.lifecycle.api.dto.ResignationResponse;
import az.millers.hcm.lifecycle.api.dto.ResignationSubmitRequest;
import az.millers.hcm.lifecycle.domain.ResignationStatus;
import az.millers.hcm.lifecycle.service.ResignationService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/lifecycle/offboarding/resignations")
public class ResignationController {

    private final ResignationService service;

    public ResignationController(ResignationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public PageResponse<ResignationResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) ResignationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(employeeId, status, PageRequest.of(page, size)),
                ResignationResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResignationResponse get(@PathVariable UUID id) {
        return ResignationResponse.from(service.get(id));
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResignationResponse submit(@Valid @RequestBody ResignationSubmitRequest req) {
        return ResignationResponse.from(service.submit(req));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResignationResponse withdraw(@PathVariable UUID id) {
        return ResignationResponse.from(service.withdraw(id));
    }

    /** HR override: change the approved last working date after workflow approval. */
    @PatchMapping("/{id}/lwd")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResignationResponse updateLwd(
            @PathVariable UUID id,
            @RequestParam LocalDate approvedLastWorkingDate) {
        return ResignationResponse.from(service.updateApprovedLwd(id, approvedLastWorkingDate));
    }
}
