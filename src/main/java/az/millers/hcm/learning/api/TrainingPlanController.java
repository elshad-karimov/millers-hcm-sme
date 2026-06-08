package az.millers.hcm.learning.api;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.learning.api.dto.TrainingPlanDtos.EnrollAllResult;
import az.millers.hcm.learning.api.dto.TrainingPlanDtos.TrainingPlanItemRequest;
import az.millers.hcm.learning.api.dto.TrainingPlanDtos.TrainingPlanRequest;
import az.millers.hcm.learning.api.dto.TrainingPlanDtos.TrainingPlanResponse;
import az.millers.hcm.learning.service.TrainingPlanService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/learning/training-plans")
public class TrainingPlanController {

    private final TrainingPlanService service;

    public TrainingPlanController(TrainingPlanService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<TrainingPlanResponse> list(@RequestParam(required = false) String status) {
        return status != null ? service.listByStatus(status) : service.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public TrainingPlanResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TrainingPlanResponse create(@Valid @RequestBody TrainingPlanRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TrainingPlanResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody TrainingPlanRequest req) {
        return service.update(id, req);
    }

    // ── Items ────────────────────────────────────────────────────────────────

    @PostMapping("/{planId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TrainingPlanResponse addItem(@PathVariable UUID planId,
                                        @Valid @RequestBody TrainingPlanItemRequest req) {
        return service.addItem(planId, req);
    }

    @DeleteMapping("/{planId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public void removeItem(@PathVariable UUID planId, @PathVariable UUID itemId) {
        service.removeItem(planId, itemId);
    }

    // ── Status transitions ───────────────────────────────────────────────────

    @PostMapping("/{id}/activate")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TrainingPlanResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TrainingPlanResponse complete(@PathVariable UUID id) {
        return service.complete(id);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TrainingPlanResponse archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    // ── Enroll-all ───────────────────────────────────────────────────────────

    @PostMapping("/{id}/enroll-all")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public EnrollAllResult enrollAll(@PathVariable UUID id) {
        return service.enrollAll(id);
    }
}
