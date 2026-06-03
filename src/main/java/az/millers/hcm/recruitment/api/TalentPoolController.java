package az.millers.hcm.recruitment.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.recruitment.api.dto.TalentPoolDtos.NoteRequest;
import az.millers.hcm.recruitment.api.dto.TalentPoolDtos.NoteResponse;
import az.millers.hcm.recruitment.api.dto.TalentPoolDtos.PoolSearchResponse;
import az.millers.hcm.recruitment.api.dto.TalentPoolDtos.PoolStatusChange;
import az.millers.hcm.recruitment.api.dto.TalentPoolDtos.TagRequest;
import az.millers.hcm.recruitment.api.dto.TalentPoolDtos.TagResponse;
import az.millers.hcm.recruitment.api.dto.CandidateResponse;
import az.millers.hcm.recruitment.domain.CandidatePoolStatus;
import az.millers.hcm.recruitment.service.TalentPoolService;
import jakarta.validation.Valid;

/**
 * Talent pool + CRM endpoints (M87).
 *
 * <ul>
 *   <li>GET /talent-pool/search — paginated candidate search with tag-set
 *       AND-filter, pool status, and free-text query</li>
 *   <li>GET /talent-pool/tags — distinct tag dictionary for the SPA's
 *       multi-select</li>
 *   <li>nested /candidates/{id}/tags — list / add / remove</li>
 *   <li>nested /candidates/{id}/notes — list / add (append-only)</li>
 *   <li>POST /candidates/{id}/pool-status — pool standing transition</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/recruitment")
public class TalentPoolController {

    private static final String READ_ROLES =
            "hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','RECRUITER','AUDITOR')";
    private static final String WRITE_ROLES =
            "hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','RECRUITER')";

    private final TalentPoolService service;

    public TalentPoolController(TalentPoolService service) {
        this.service = service;
    }

    // ── Pool search ──────────────────────────────────────────────────────────

    @GetMapping("/talent-pool/search")
    @PreAuthorize(READ_ROLES)
    public PoolSearchResponse search(
            @RequestParam(required = false) CandidatePoolStatus status,
            @RequestParam(required = false) List<String> tag,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(status, tag, q, page, size);
    }

    @GetMapping("/talent-pool/tags")
    @PreAuthorize(READ_ROLES)
    public List<String> knownTags() {
        return service.knownTags();
    }

    // ── Per-candidate tags ───────────────────────────────────────────────────

    @GetMapping("/candidates/{id}/tags")
    @PreAuthorize(READ_ROLES)
    public List<TagResponse> tagsOf(@PathVariable UUID id) {
        return service.tagsOf(id).stream().map(TagResponse::from).toList();
    }

    @PostMapping("/candidates/{id}/tags")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public TagResponse addTag(@PathVariable UUID id,
                                @Valid @RequestBody TagRequest req) {
        return TagResponse.from(service.addTag(id, req.tag()));
    }

    @DeleteMapping("/candidates/{id}/tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void removeTag(@PathVariable UUID id, @PathVariable UUID tagId) {
        service.removeTag(id, tagId);
    }

    // ── Per-candidate notes ──────────────────────────────────────────────────

    @GetMapping("/candidates/{id}/notes")
    @PreAuthorize(READ_ROLES)
    public List<NoteResponse> notesOf(@PathVariable UUID id) {
        return service.notesOf(id).stream().map(NoteResponse::from).toList();
    }

    @PostMapping("/candidates/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public NoteResponse addNote(@PathVariable UUID id,
                                  @Valid @RequestBody NoteRequest req) {
        return NoteResponse.from(service.addNote(id, req));
    }

    // ── Pool status ──────────────────────────────────────────────────────────

    @PostMapping("/candidates/{id}/pool-status")
    @PreAuthorize(WRITE_ROLES)
    public CandidateResponse changePoolStatus(@PathVariable UUID id,
                                                @Valid @RequestBody PoolStatusChange req) {
        return CandidateResponse.from(service.changePoolStatus(id, req));
    }
}
