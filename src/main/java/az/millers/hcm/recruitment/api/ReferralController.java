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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.recruitment.api.dto.ReferralDtos.ReferralRequest;
import az.millers.hcm.recruitment.api.dto.ReferralDtos.ReferralResponse;
import az.millers.hcm.recruitment.domain.ReferralStatus;
import az.millers.hcm.recruitment.service.ReferralService;
import az.millers.hcm.security.SecurityRoles;

/** M295 — Recruitment PRD Phase F referral-program REST. */
@RestController
@RequestMapping("/api/recruitment/referrals")
public class ReferralController {

    private final ReferralService service;

    public ReferralController(ReferralService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_RECRUITMENT)
    public List<ReferralResponse> list(@RequestParam(required = false) ReferralStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_RECRUITMENT)
    public ReferralResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public ReferralResponse create(@Valid @RequestBody ReferralRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public ReferralResponse reject(@PathVariable UUID id,
                                    @RequestParam(required = false) String reason) {
        return service.reject(id, reason);
    }

    /** Pay a QUALIFIED referral into a payroll run (payroll-affecting). */
    @PostMapping("/{id}/pay")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReferralResponse pay(@PathVariable UUID id, @RequestParam UUID payrollRunId) {
        return service.pay(id, payrollRunId);
    }

    /** Manually run the qualifying sweep (also runs daily on a schedule). */
    @PostMapping("/promote-qualified")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public Map<String, Integer> promoteQualified() {
        return Map.of("promoted", service.promoteQualified());
    }
}
