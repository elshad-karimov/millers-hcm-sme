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

import az.millers.hcm.learning.api.dto.LearningPathCourseRequest;
import az.millers.hcm.learning.api.dto.LearningPathRequest;
import az.millers.hcm.learning.api.dto.LearningPathResponse;
import az.millers.hcm.learning.service.LearningPathService;
import jakarta.validation.Valid;

/**
 * REST API for learning paths (M46).
 *
 * <pre>
 * GET    /api/learning/paths              — list (optional ?active=true)
 * GET    /api/learning/paths/{id}         — single path + steps
 * POST   /api/learning/paths              — create (ADMIN/HR_MANAGER)
 * PUT    /api/learning/paths/{id}         — update (ADMIN/HR_MANAGER)
 * POST   /api/learning/paths/{id}/courses — add step (ADMIN/HR_MANAGER)
 * DELETE /api/learning/paths/{id}/courses/{stepId} — remove step
 * </pre>
 */
@RestController
@RequestMapping("/api/learning/paths")
public class LearningPathController {

    private final LearningPathService service;

    public LearningPathController(LearningPathService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<LearningPathResponse> list(@RequestParam(required = false) Boolean active) {
        return service.list(active);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public LearningPathResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public LearningPathResponse create(@Valid @RequestBody LearningPathRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public LearningPathResponse update(@PathVariable UUID id,
                                        @Valid @RequestBody LearningPathRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/courses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public LearningPathResponse addCourse(@PathVariable UUID id,
                                           @Valid @RequestBody LearningPathCourseRequest req) {
        return service.addCourse(id, req);
    }

    @DeleteMapping("/{id}/courses/{stepId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public LearningPathResponse removeCourse(@PathVariable UUID id,
                                              @PathVariable UUID stepId) {
        return service.removeCourse(id, stepId);
    }
}
