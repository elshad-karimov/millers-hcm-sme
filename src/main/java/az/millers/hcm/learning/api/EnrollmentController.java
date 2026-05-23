package az.millers.hcm.learning.api;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.learning.api.dto.AttemptResponse;
import az.millers.hcm.learning.api.dto.AttemptSubmitRequest;
import az.millers.hcm.learning.api.dto.EnrollRequest;
import az.millers.hcm.learning.api.dto.EnrollmentResponse;
import az.millers.hcm.learning.domain.Enrollment;
import az.millers.hcm.learning.domain.EnrollmentStatus;
import az.millers.hcm.learning.domain.QuizAttempt;
import az.millers.hcm.learning.service.EnrollmentService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/learning/enrollments")
public class EnrollmentController {

    private final EnrollmentService service;

    public EnrollmentController(EnrollmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<EnrollmentResponse> list(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Enrollment> result = service.list(courseId, employeeId, status, PageRequest.of(page, size));
        return PageResponse.of(result, EnrollmentResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public EnrollmentResponse get(@PathVariable UUID id) {
        return EnrollmentResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public EnrollmentResponse enroll(@Valid @RequestBody EnrollRequest req) {
        return EnrollmentResponse.from(service.enroll(req));
    }

    @GetMapping("/{id}/attempts")
    @PreAuthorize("isAuthenticated()")
    public List<AttemptResponse> attempts(@PathVariable UUID id) {
        return service.attemptsFor(id).stream()
                .map(a -> AttemptResponse.from(a, java.util.List.of()))
                .toList();
    }

    @PostMapping("/{id}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public AttemptResponse startAttempt(@PathVariable UUID id) {
        QuizAttempt a = service.startAttempt(id);
        return AttemptResponse.from(a, java.util.List.of());
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @PreAuthorize("isAuthenticated()")
    public AttemptResponse submitAttempt(@PathVariable UUID attemptId,
                                          @Valid @RequestBody AttemptSubmitRequest req) {
        return service.submitAttempt(attemptId, req);
    }

    @GetMapping("/attempts/{attemptId}")
    @PreAuthorize("isAuthenticated()")
    public AttemptResponse getAttempt(@PathVariable UUID attemptId) {
        return service.getAttempt(attemptId);
    }
}
