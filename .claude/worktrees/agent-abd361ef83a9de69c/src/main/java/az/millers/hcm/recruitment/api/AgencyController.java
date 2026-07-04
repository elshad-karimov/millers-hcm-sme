package az.millers.hcm.recruitment.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

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

import az.millers.hcm.recruitment.api.dto.AgencyDtos.AgencyRequest;
import az.millers.hcm.recruitment.api.dto.AgencyDtos.AgencyResponse;
import az.millers.hcm.recruitment.api.dto.AgencyDtos.InvoiceResponse;
import az.millers.hcm.recruitment.api.dto.AgencyDtos.InvoiceUpdate;
import az.millers.hcm.recruitment.api.dto.AgencyDtos.SubmissionRequest;
import az.millers.hcm.recruitment.api.dto.AgencyDtos.SubmissionResponse;
import az.millers.hcm.recruitment.domain.InvoiceStatus;
import az.millers.hcm.recruitment.domain.SubmissionStatus;
import az.millers.hcm.recruitment.service.AgencyService;
import az.millers.hcm.security.SecurityRoles;

/** M296 — Recruitment PRD Phase F agency / submission / invoice REST. */
@RestController
@RequestMapping("/api/recruitment")
public class AgencyController {

    private final AgencyService service;

    public AgencyController(AgencyService service) {
        this.service = service;
    }

    // ── Agency master ──────────────────────────────────────────────────

    @GetMapping("/agencies")
    @PreAuthorize(SecurityRoles.READ_RECRUITMENT)
    public List<AgencyResponse> agencies(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.listAgencies(activeOnly);
    }

    @PostMapping("/agencies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AgencyResponse createAgency(@Valid @RequestBody AgencyRequest req) {
        return service.createAgency(req);
    }

    @PutMapping("/agencies/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AgencyResponse updateAgency(@PathVariable UUID id, @Valid @RequestBody AgencyRequest req) {
        return service.updateAgency(id, req);
    }

    // ── Submissions ────────────────────────────────────────────────────

    @GetMapping("/agency-submissions")
    @PreAuthorize(SecurityRoles.READ_RECRUITMENT)
    public List<SubmissionResponse> submissions(@RequestParam(required = false) SubmissionStatus status) {
        return service.listSubmissions(status);
    }

    @PostMapping("/agency-submissions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public SubmissionResponse submit(@Valid @RequestBody SubmissionRequest req) {
        return service.createSubmission(req);
    }

    @PostMapping("/agency-submissions/{id}/withdraw")
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public SubmissionResponse withdraw(@PathVariable UUID id) {
        return service.withdrawSubmission(id);
    }

    @PostMapping("/agency-submissions/expire-overdue")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public Map<String, Integer> expireOverdue() {
        return Map.of("expired", service.expireOverdue());
    }

    // ── Invoices ───────────────────────────────────────────────────────

    @GetMapping("/agency-invoices")
    @PreAuthorize(SecurityRoles.READ_RECRUITMENT)
    public List<InvoiceResponse> invoices(@RequestParam(required = false) InvoiceStatus status) {
        return service.listInvoices(status);
    }

    @PutMapping("/agency-invoices/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public InvoiceResponse updateInvoice(@PathVariable UUID id, @Valid @RequestBody InvoiceUpdate req) {
        return service.updateInvoice(id, req);
    }
}
