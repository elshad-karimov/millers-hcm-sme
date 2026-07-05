package az.millers.hcm.selfservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.employeerelations.domain.WarningRecord;
import az.millers.hcm.employeerelations.service.WarningService;

/**
 * M446 — Self-service warning endpoints.
 */
@RestController
@RequestMapping("/api/self/warnings")
public class SelfWarningController {

    private final WarningService service;

    public SelfWarningController(WarningService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<WarningRecord> myWarnings() {
        return service.myWarnings();
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("isAuthenticated()")
    public WarningRecord acknowledge(@PathVariable UUID id) {
        return service.acknowledge(id);
    }
}
