package az.millers.hcm.learning.api;

import az.millers.hcm.security.SecurityRoles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.learning.api.dto.CourseCompetencyRequest;
import az.millers.hcm.learning.api.dto.CourseRequest;
import az.millers.hcm.learning.api.dto.CourseResponse;
import az.millers.hcm.learning.api.dto.QuestionRequest;
import az.millers.hcm.learning.api.dto.QuestionResponse;
import az.millers.hcm.learning.domain.Course;
import az.millers.hcm.learning.domain.CourseCategory;
import az.millers.hcm.learning.domain.CourseStatus;
import az.millers.hcm.learning.service.CourseService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/learning/courses")
public class CourseController {

    private final CourseService service;

    public CourseController(CourseService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CourseResponse> list(
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) CourseCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Course> result = service.list(status, category, PageRequest.of(page, size));
        return PageResponse.of(result, CourseResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CourseResponse get(@PathVariable UUID id) {
        return CourseResponse.from(service.get(id));
    }

    /**
     * Questions endpoint. Includes the answer key only for HR/admin roles;
     * employees taking the quiz get the questions without the correct keys.
     */
    @GetMapping("/{id}/questions")
    @PreAuthorize("isAuthenticated()")
    public List<QuestionResponse> questions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean includeAnswers,
            org.springframework.security.core.Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SYSTEM_ADMIN")
                        || a.getAuthority().equals("ROLE_HR_ADMIN")
                        || a.getAuthority().equals("ROLE_HR_SPECIALIST"));
        boolean withKey = includeAnswers && isAdmin;
        return service.questionsFor(id).stream()
                .map(q -> QuestionResponse.from(q, withKey))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public CourseResponse create(@Valid @RequestBody CourseRequest req) {
        return CourseResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public CourseResponse update(@PathVariable UUID id, @Valid @RequestBody CourseRequest req) {
        return CourseResponse.from(service.update(id, req));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public CourseResponse publish(@PathVariable UUID id) {
        return CourseResponse.from(service.publish(id));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public CourseResponse archive(@PathVariable UUID id) {
        return CourseResponse.from(service.archive(id));
    }

    @PostMapping("/{id}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public QuestionResponse addQuestion(@PathVariable UUID id,
                                          @Valid @RequestBody QuestionRequest req) {
        return QuestionResponse.from(service.addQuestion(id, req), true);
    }

    @PostMapping("/{id}/competencies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public Map<String, Object> mapCompetency(@PathVariable UUID id,
                                              @Valid @RequestBody CourseCompetencyRequest req) {
        service.mapCompetency(id, req);
        return Map.of("courseId", id, "competencyId", req.competencyId(),
                "awardedLevel", req.awardedLevel() == null ? 3 : req.awardedLevel());
    }

    @GetMapping("/{id}/competencies")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> listMappings(@PathVariable UUID id) {
        return service.competencyMappingFor(id).stream()
                .map(m -> Map.<String, Object>of(
                        "competencyId", m.getCompetencyId(),
                        "awardedLevel", m.getAwardedLevel()))
                .toList();
    }
}
