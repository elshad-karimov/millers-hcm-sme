package az.millers.hcm.lifecycle.api;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.OffboardingReportResponse;
import az.millers.hcm.lifecycle.service.OffboardingAnalyticsService;
import az.millers.hcm.security.SecurityRoles;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/lifecycle/offboarding")
@RequiredArgsConstructor
public class OffboardingAnalyticsController {

    private final OffboardingAnalyticsService service;

    @GetMapping("/analytics")
    @PreAuthorize(SecurityRoles.READ_HR)
    public OffboardingReportResponse report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(from, to);
    }
}
