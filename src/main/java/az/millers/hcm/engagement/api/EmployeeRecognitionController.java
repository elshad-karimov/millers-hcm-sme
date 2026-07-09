package az.millers.hcm.engagement.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.engagement.domain.EmployeeRecognition;
import az.millers.hcm.engagement.domain.RecognitionStatus;
import az.millers.hcm.engagement.service.EmployeeRecognitionService;

/**
 * M478 — Recognition/kudos endpoints.
 */
@RestController
@RequestMapping("/api/engagement/recognition")
public class EmployeeRecognitionController {

    private final EmployeeRecognitionService service;

    public EmployeeRecognitionController(EmployeeRecognitionService service) {
        this.service = service;
    }

    /**
     * Public wall: recent PUBLIC+ACTIVE recognitions with names (paginated).
     */
    @GetMapping("/wall")
    @PreAuthorize("hasAnyRole('EMPLOYEE','HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER')")
    public List<Map<String, Object>> publicWall(@RequestParam(defaultValue = "50") int limit) {
        return service.publicWall(limit);
    }

    /**
     * My sent recognitions.
     */
    @GetMapping("/my-sent")
    @PreAuthorize("hasAnyRole('EMPLOYEE','HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER')")
    public List<EmployeeRecognition> mySent() {
        return service.mySent();
    }

    /**
     * My received recognitions.
     */
    @GetMapping("/my-received")
    @PreAuthorize("hasAnyRole('EMPLOYEE','HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER')")
    public List<EmployeeRecognition> myReceived() {
        return service.myReceived();
    }

    /**
     * Send recognition (from = current employee, never spoofable).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER')")
    public EmployeeRecognition send(@RequestBody SendRequest req) {
        EmployeeRecognition recognition = new EmployeeRecognition();
        recognition.setValueTag(req.valueTag);
        recognition.setMessage(req.message);
        recognition.setVisibility(req.visibility);
        return service.send(req.toEmployeeId, recognition);
    }

    /**
     * HR hide/unhide (audited).
     */
    @PostMapping("/{id}/hide")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public EmployeeRecognition hide(@PathVariable UUID id) {
        return service.updateStatus(id, RecognitionStatus.HIDDEN);
    }

    @PostMapping("/{id}/unhide")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public EmployeeRecognition unhide(@PathVariable UUID id) {
        return service.updateStatus(id, RecognitionStatus.ACTIVE);
    }

    public record SendRequest(
            UUID toEmployeeId,
            az.millers.hcm.engagement.domain.RecognitionValueTag valueTag,
            String message,
            az.millers.hcm.engagement.domain.RecognitionVisibility visibility) {}
}
