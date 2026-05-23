package az.millers.hcm.performance.api;

import java.util.List;
import java.util.UUID;

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

import az.millers.hcm.performance.api.dto.FeedbackRequest;
import az.millers.hcm.performance.api.dto.FeedbackResponse;
import az.millers.hcm.performance.service.FeedbackService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/performance/feedback")
public class FeedbackController {

    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<FeedbackResponse> list(@RequestParam UUID cycleId,
                                        @RequestParam(required = false) UUID subjectEmployeeId) {
        var rows = subjectEmployeeId == null
                ? service.listForCycle(cycleId)
                : service.listForSubject(cycleId, subjectEmployeeId);
        return rows.stream().map(FeedbackResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public FeedbackResponse submit(@Valid @RequestBody FeedbackRequest req) {
        return FeedbackResponse.from(service.submit(req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public FeedbackResponse submitDraft(@PathVariable UUID id) {
        return FeedbackResponse.from(service.submitDraft(id));
    }
}
