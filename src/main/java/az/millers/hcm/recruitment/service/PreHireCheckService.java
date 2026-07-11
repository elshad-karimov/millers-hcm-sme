package az.millers.hcm.recruitment.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.PreHireCheckDtos.CheckRequest;
import az.millers.hcm.recruitment.api.dto.PreHireCheckDtos.CheckResponse;
import az.millers.hcm.recruitment.api.dto.PreHireCheckDtos.CheckUpdate;
import az.millers.hcm.recruitment.domain.PreHireCheck;
import az.millers.hcm.recruitment.repo.ApplicationRepository;
import az.millers.hcm.recruitment.repo.PreHireCheckRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.common.BusinessNumbers;

/**
 * M286 — Recruitment PRD §25-§27: pre-hire checks.
 *
 * <p>Manages background / reference / medical / … checks on an
 * application. Confidential result detail (MEDICAL) is redacted in
 * responses unless the caller is HR_ADMIN / SYSTEM_ADMIN (§27), and a
 * FAILED check that blocks-hire stops {@code ApplicationService.hire}
 * (§25).
 */
@Service
public class PreHireCheckService {

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "PreHireCheck";

    private final PreHireCheckRepository checks;
    private final ApplicationRepository applications;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PreHireCheckService(PreHireCheckRepository checks,
                                ApplicationRepository applications,
                                AuditService audit,
                                CurrentRequest currentRequest) {
        this.checks = checks;
        this.applications = applications;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<CheckResponse> listForApplication(UUID applicationId) {
        boolean privileged = callerSeesConfidential();
        return checks.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream()
                .map(c -> CheckResponse.from(c, redact(c, privileged)))
                .toList();
    }

    @Transactional
    public CheckResponse create(UUID applicationId, CheckRequest req) {
        if (!applications.existsById(applicationId)) {
            throw new ResourceNotFoundException("Application not found: " + applicationId);
        }
        PreHireCheck c = new PreHireCheck();
        c.setCheckNo(BusinessNumbers.format("CHK", 5, checks.nextNoSequence()));
        c.setApplicationId(applicationId);
        c.setCheckType(req.checkType());
        c.setStatus(PreHireCheck.Status.REQUIRED);
        c.setProvider(req.provider());
        c.setSubjectName(req.subjectName());
        c.setSubjectContact(req.subjectContact());
        c.setBlocksHire(req.blocksHire() == null ? true : req.blocksHire());
        c.setCreatedBy(currentRequest.username());
        c.setUpdatedBy(currentRequest.username());
        PreHireCheck saved = checks.save(c);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null,
                Map.of("applicationId", applicationId.toString(),
                        "type", saved.getCheckType().name(),
                        "blocksHire", saved.isBlocksHire()));
        return CheckResponse.from(saved, redact(saved, callerSeesConfidential()));
    }

    @Transactional
    public CheckResponse update(UUID id, CheckUpdate req) {
        PreHireCheck c = checks.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Check not found: " + id));
        if (c.getStatus() == PreHireCheck.Status.CANCELLED) {
            throw new BadRequestException("Check is cancelled");
        }
        PreHireCheck.Status old = c.getStatus();
        c.setStatus(req.status());
        c.setResult(req.result());
        if (req.resultNotes() != null) c.setResultNotes(req.resultNotes());
        if (req.attachmentId() != null) c.setAttachmentId(req.attachmentId());

        // Timestamp transitions.
        if (req.status() == PreHireCheck.Status.REQUESTED && c.getRequestedAt() == null) {
            c.setRequestedAt(OffsetDateTime.now());
        }
        if ((req.status() == PreHireCheck.Status.COMPLETED
                || req.status() == PreHireCheck.Status.PASSED
                || req.status() == PreHireCheck.Status.FAILED)
                && c.getCompletedAt() == null) {
            c.setCompletedAt(OffsetDateTime.now());
        }
        c.setUpdatedBy(currentRequest.username());
        PreHireCheck saved = checks.save(c);
        // Audit never carries the confidential note body — only the
        // status/result transition (PRD §27 keeps medical detail out
        // of the general audit browser).
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE",
                Map.of("status", old.name()),
                Map.of("status", saved.getStatus().name(),
                        "result", saved.getResult() == null ? "" : saved.getResult().name()));
        return CheckResponse.from(saved, redact(saved, callerSeesConfidential()));
    }

    /**
     * M286 — PRD §25 hire gate. Throws when any blocks-hire check on the
     * application has FAILED. Called from {@code ApplicationService.hire}.
     */
    @Transactional(readOnly = true)
    public void assertNoBlockingFailures(UUID applicationId) {
        List<PreHireCheck> failed = checks.findBlockingFailures(applicationId);
        if (!failed.isEmpty()) {
            String types = failed.stream()
                    .map(c -> c.getCheckType().name())
                    .distinct()
                    .reduce((x, y) -> x + ", " + y).orElse("");
            throw new BadRequestException(
                    "Cannot hire — required pre-hire check(s) FAILED: " + types
                    + ". Resolve or waive before hiring.");
        }
    }

    /** Redact a confidential check's notes unless the caller is privileged. */
    private boolean redact(PreHireCheck c, boolean privileged) {
        return c.getCheckType().isConfidential() && !privileged;
    }

    /** HR_ADMIN / SYSTEM_ADMIN may see confidential (medical) detail. */
    private boolean callerSeesConfidential() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .anyMatch(r -> r.equals("ROLE_HR_ADMIN") || r.equals("ROLE_SYSTEM_ADMIN"));
    }
}
