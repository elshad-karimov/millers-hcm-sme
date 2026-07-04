package az.millers.hcm.performance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.performance.service.MentoringService;
import az.millers.hcm.performance.service.MentoringService.MentorProfileRequest;
import az.millers.hcm.performance.service.MentoringService.MentorProfileResponse;
import az.millers.hcm.performance.service.MentoringService.RelationshipResponse;

/**
 * HCM_16 M417 — mentoring (PRD §16.7).
 * Mentees request; mentor/HR approve. Confidentiality enforced in service.
 */
@RestController
@RequestMapping("/api/talent/mentoring")
public class MentoringController {

    private final MentoringService service;

    public MentoringController(MentoringService service) {
        this.service = service;
    }

    @PostMapping("/mentors")
    @PreAuthorize("isAuthenticated()")
    public MentorProfileResponse registerMentor(@RequestBody MentorProfileRequest req) {
        return service.registerMentor(req);
    }

    @GetMapping("/mentors")
    @PreAuthorize("isAuthenticated()")
    public List<MentorProfileResponse> listMentors() {
        return service.listMentors();
    }

    @PostMapping("/relationships/request/{mentorProfileId}")
    @PreAuthorize("isAuthenticated()")
    public RelationshipResponse request(@PathVariable UUID mentorProfileId) {
        return service.request(mentorProfileId);
    }

    @PutMapping("/relationships/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public RelationshipResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PutMapping("/relationships/{id}/complete")
    @PreAuthorize("isAuthenticated()")
    public RelationshipResponse complete(@PathVariable UUID id) {
        return service.complete(id);
    }

    @PutMapping("/relationships/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public RelationshipResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @PutMapping("/relationships/{id}/notes")
    @PreAuthorize("isAuthenticated()")
    public RelationshipResponse updateNotes(@PathVariable UUID id, @RequestBody NotesRequest req) {
        return service.updateNotes(id, req.notes);
    }

    @GetMapping("/relationships")
    @PreAuthorize("isAuthenticated()")
    public List<RelationshipResponse> listRelationships() {
        return service.listRelationships();
    }

    public record NotesRequest(String notes) {}
}
