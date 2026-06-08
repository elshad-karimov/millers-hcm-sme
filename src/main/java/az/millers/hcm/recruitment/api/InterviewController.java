package az.millers.hcm.recruitment.api;

import java.util.List;
import java.util.UUID;

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

import az.millers.hcm.recruitment.api.dto.InterviewDtos.InterviewDetail;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.InterviewFinalize;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.InterviewReschedule;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.InterviewResponse;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.InterviewSchedule;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.ScoreResponse;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.ScoreUpsert;
import az.millers.hcm.recruitment.domain.InterviewStatus;
import az.millers.hcm.recruitment.service.InterviewService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recruitment/interviews")
public class InterviewController {

    /** Centralised role sets — see {@link az.millers.hcm.security.SecurityRoles}. */
    private static final String READ_ROLES = az.millers.hcm.security.SecurityRoles.READ_INTERVIEWS;
    private static final String WRITE_ROLES = az.millers.hcm.security.SecurityRoles.WRITE_INTERVIEWS;

    private final InterviewService service;

    public InterviewController(InterviewService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<InterviewResponse> list(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) UUID interviewerEmployeeId,
            @RequestParam(required = false) InterviewStatus status) {
        if (applicationId != null) {
            return service.listForApplication(applicationId).stream()
                    .map(InterviewResponse::from).toList();
        }
        if (interviewerEmployeeId != null) {
            return service.listForInterviewer(interviewerEmployeeId, status).stream()
                    .map(InterviewResponse::from).toList();
        }
        throw new IllegalArgumentException(
                "applicationId or interviewerEmployeeId is required");
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public InterviewDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public InterviewResponse schedule(@Valid @RequestBody InterviewSchedule req) {
        return InterviewResponse.from(service.schedule(req));
    }

    @PostMapping("/{id}/scores")
    @PreAuthorize(WRITE_ROLES)
    public ScoreResponse upsertScore(@PathVariable UUID id,
                                       @Valid @RequestBody ScoreUpsert req) {
        return ScoreResponse.from(service.upsertScore(id, req));
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize(WRITE_ROLES)
    public InterviewResponse finalize_(@PathVariable UUID id,
                                         @Valid @RequestBody InterviewFinalize req) {
        return InterviewResponse.from(service.finalize_(id, req));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(WRITE_ROLES)
    public InterviewResponse cancel(@PathVariable UUID id,
                                      @RequestParam(required = false) String reason) {
        return InterviewResponse.from(service.cancel(id, reason));
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize(WRITE_ROLES)
    public InterviewResponse noShow(@PathVariable UUID id,
                                      @RequestParam(required = false) String reason) {
        return InterviewResponse.from(service.markNoShow(id, reason));
    }

    /** M219 — Update scheduledAt (and optionally interviewer/kit) on a non-terminal interview. */
    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public InterviewResponse reschedule(@PathVariable UUID id,
                                         @Valid @RequestBody InterviewReschedule req) {
        return InterviewResponse.from(service.reschedule(id, req));
    }
}
