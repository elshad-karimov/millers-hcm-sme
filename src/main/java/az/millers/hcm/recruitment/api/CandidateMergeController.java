package az.millers.hcm.recruitment.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.recruitment.api.dto.CandidateMergeDtos.DuplicateGroup;
import az.millers.hcm.recruitment.api.dto.CandidateMergeDtos.MergeRequest;
import az.millers.hcm.recruitment.api.dto.CandidateMergeDtos.MergeResult;
import az.millers.hcm.recruitment.service.CandidateMergeService;
import az.millers.hcm.security.SecurityRoles;

/** M293 — Recruitment PRD §12 duplicate-detection + merge REST. */
@RestController
@RequestMapping("/api/recruitment/candidates")
public class CandidateMergeController {

    private final CandidateMergeService service;

    public CandidateMergeController(CandidateMergeService service) {
        this.service = service;
    }

    /** Candidate groups that look like the same person. */
    @GetMapping("/duplicates")
    @PreAuthorize(SecurityRoles.READ_RECRUITMENT)
    public List<DuplicateGroup> duplicates() {
        return service.findDuplicates();
    }

    /** Merge the duplicate into the primary (primary survives). */
    @PostMapping("/merge")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public MergeResult merge(@Valid @RequestBody MergeRequest req) {
        return service.merge(req.primaryId(), req.duplicateId());
    }
}
