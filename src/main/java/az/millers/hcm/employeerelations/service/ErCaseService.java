package az.millers.hcm.employeerelations.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.employeerelations.domain.*;
import az.millers.hcm.employeerelations.repo.*;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.common.BusinessNumbers;

/**
 * M445 — ER case service.
 * Confidential cases visible ONLY to HR_ADMIN + owner.
 */
@Service
public class ErCaseService {

    private static final String MODULE = "employee-relations";
    private static final String ENTITY = "ErCase";

    private final ErCaseRepository caseRepo;
    private final ErCaseNoteRepository noteRepo;
    private final ErInvestigationRepository investigationRepo;
    private final ErInvestigationInterviewRepository interviewRepo;
    private final ErEvidenceRepository evidenceRepo;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ErCaseService(ErCaseRepository caseRepo,
                        ErCaseNoteRepository noteRepo,
                        ErInvestigationRepository investigationRepo,
                        ErInvestigationInterviewRepository interviewRepo,
                        ErEvidenceRepository evidenceRepo,
                        NamedParameterJdbcTemplate jdbc,
                        AuditService audit,
                        CurrentRequest currentRequest) {
        this.caseRepo = caseRepo;
        this.noteRepo = noteRepo;
        this.investigationRepo = investigationRepo;
        this.interviewRepo = interviewRepo;
        this.evidenceRepo = evidenceRepo;
        this.jdbc = jdbc;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public ErCase create(ErCaseType caseType,
                        String category,
                        ErCaseSeverity severity,
                        UUID employeeId,
                        String description,
                        Boolean isConfidential,
                        Boolean isAnonymous) {
        ErCase erCase = new ErCase();
        erCase.setTenantId(TenantContext.current());
        erCase.setCaseType(caseType);
        erCase.setCategory(category);
        erCase.setSeverity(severity);
        erCase.setEmployeeId(isAnonymous != null && isAnonymous ? null : employeeId);
        erCase.setDescription(description);
        erCase.setIsConfidential(isConfidential != null ? isConfidential : false);
        erCase.setStatus(ErCaseStatus.OPEN);
        erCase.setCreatedBy(currentRequest.username());
        erCase.setUpdatedBy(currentRequest.username());

        ErCase saved = caseRepo.save(erCase);

        // Generate case number
        String caseNo = BusinessNumbers.format("ER", 5, jdbc.queryForObject(
                "SELECT config.next_tenant_seq('employee_relations.er_case_no_seq')",
                Map.of(), Long.class));
        saved.setCaseNo(caseNo);
        saved = caseRepo.save(saved);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATED", null, null);

        return saved;
    }

    @Transactional
    public ErCase updateStatus(UUID id, ErCaseStatus newStatus, String outcome) {
        ErCase erCase = get(id);
        checkConfidentialAccess(erCase);

        // Legal hold blocks CLOSED
        if (newStatus == ErCaseStatus.CLOSED && erCase.getLegalHold()) {
            throw new BadRequestException("Cannot close case with active legal hold");
        }

        ErCaseStatus oldStatus = erCase.getStatus();
        erCase.setStatus(newStatus);
        erCase.setUpdatedBy(currentRequest.username());
        erCase.setUpdatedAt(OffsetDateTime.now());

        if (newStatus == ErCaseStatus.CLOSED) {
            erCase.setClosedAt(OffsetDateTime.now());
        }

        if (outcome != null) {
            erCase.setOutcome(outcome);
        }

        ErCase updated = caseRepo.save(erCase);

        audit.record(MODULE, ENTITY, id.toString(), "STATUS_CHANGE",
                Map.of("status", oldStatus),
                Map.of("status", newStatus, "outcome", outcome));

        return updated;
    }

    @Transactional
    public ErCase updateDetails(UUID id, String category, ErCaseSeverity severity,
                                String description, String ownerUsername,
                                Boolean legalHold) {
        ErCase erCase = get(id);
        checkConfidentialAccess(erCase);

        if (category != null) {
            erCase.setCategory(category);
        }
        if (severity != null) {
            erCase.setSeverity(severity);
        }
        if (description != null) {
            erCase.setDescription(description);
        }
        if (ownerUsername != null) {
            erCase.setOwnerUsername(ownerUsername);
        }
        if (legalHold != null) {
            // Only HR_ADMIN can set legal hold
            if (!currentRequest.hasRole("HR_ADMIN")) {
                throw new BadRequestException("Only HR_ADMIN can modify legal hold");
            }
            erCase.setLegalHold(legalHold);
        }

        erCase.setUpdatedBy(currentRequest.username());
        erCase.setUpdatedAt(OffsetDateTime.now());

        ErCase updated = caseRepo.save(erCase);

        audit.record(MODULE, ENTITY, id.toString(), "UPDATED", null, null);

        return updated;
    }

    @Transactional(readOnly = true)
    public ErCase get(UUID id) {
        ErCase erCase = caseRepo.findByIdAndTenantId(id, TenantContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("ER case not found: " + id));

        // Enforce confidential visibility: only HR_ADMIN or owner
        checkConfidentialAccess(erCase);

        return erCase;
    }

    @Transactional(readOnly = true)
    public List<ErCase> list(ErCaseType typeFilter, ErCaseSeverity severityFilter,
                             ErCaseStatus statusFilter) {
        List<ErCase> cases = caseRepo.findByTenantIdOrderByCreatedAtDesc(TenantContext.current());

        // Filter by confidentiality — only HR_ADMIN or owner sees confidential cases
        cases = filterConfidential(cases);

        if (typeFilter != null) {
            cases = cases.stream()
                    .filter(c -> c.getCaseType() == typeFilter)
                    .collect(Collectors.toList());
        }
        if (severityFilter != null) {
            cases = cases.stream()
                    .filter(c -> c.getSeverity() == severityFilter)
                    .collect(Collectors.toList());
        }
        if (statusFilter != null) {
            cases = cases.stream()
                    .filter(c -> c.getStatus() == statusFilter)
                    .collect(Collectors.toList());
        }

        return cases;
    }

    @Transactional
    public ErCaseNote addNote(UUID caseId, String body, Boolean isInternal) {
        ErCase erCase = get(caseId);
        checkConfidentialAccess(erCase);

        ErCaseNote note = new ErCaseNote();
        note.setTenantId(TenantContext.current());
        note.setCaseId(caseId);
        note.setBody(body);
        note.setIsInternal(isInternal != null ? isInternal : false);
        note.setCreatedBy(currentRequest.username());

        ErCaseNote saved = noteRepo.save(note);

        audit.record(MODULE, "ErCaseNote", saved.getId().toString(), "ADDED", null, null);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ErCaseNote> getNotes(UUID caseId) {
        ErCase erCase = get(caseId);
        checkConfidentialAccess(erCase);
        return noteRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
    }

    @Transactional
    public ErInvestigation openInvestigation(UUID caseId, String investigatorUsername) {
        ErCase erCase = get(caseId);
        checkConfidentialAccess(erCase);

        ErInvestigation investigation = new ErInvestigation();
        investigation.setTenantId(TenantContext.current());
        investigation.setCaseId(caseId);
        investigation.setInvestigatorUsername(investigatorUsername);
        investigation.setStatus(ErInvestigationStatus.OPEN);
        investigation.setCreatedBy(currentRequest.username());

        ErInvestigation saved = investigationRepo.save(investigation);

        // Update case status
        if (erCase.getStatus() == ErCaseStatus.OPEN) {
            erCase.setStatus(ErCaseStatus.UNDER_INVESTIGATION);
            erCase.setUpdatedBy(currentRequest.username());
            erCase.setUpdatedAt(OffsetDateTime.now());
            caseRepo.save(erCase);
        }

        audit.record(MODULE, "ErInvestigation", saved.getId().toString(), "OPENED", null, null);

        return saved;
    }

    @Transactional
    public ErInvestigation closeInvestigation(UUID investigationId, String findings,
                                              String recommendation) {
        ErInvestigation investigation = investigationRepo.findByIdAndTenantId(investigationId, TenantContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("Investigation not found: " + investigationId));

        ErCase erCase = get(investigation.getCaseId());
        checkConfidentialAccess(erCase);

        investigation.setFindings(findings);
        investigation.setRecommendation(recommendation);
        investigation.setStatus(ErInvestigationStatus.CLOSED);
        investigation.setClosedAt(OffsetDateTime.now());

        ErInvestigation updated = investigationRepo.save(investigation);

        audit.record(MODULE, "ErInvestigation", investigationId.toString(), "CLOSED", null, null);

        return updated;
    }

    @Transactional(readOnly = true)
    public List<ErInvestigation> getInvestigations(UUID caseId) {
        ErCase erCase = get(caseId);
        checkConfidentialAccess(erCase);
        return investigationRepo.findByCaseIdOrderByCreatedAtAsc(caseId);
    }

    @Transactional
    public ErInvestigationInterview addInterview(UUID investigationId,
                                                 String intervieweeName,
                                                 String intervieweeRole,
                                                 java.time.LocalDate interviewDate,
                                                 String summary) {
        ErInvestigation investigation = investigationRepo.findByIdAndTenantId(investigationId, TenantContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("Investigation not found: " + investigationId));

        ErCase erCase = get(investigation.getCaseId());
        checkConfidentialAccess(erCase);

        ErInvestigationInterview interview = new ErInvestigationInterview();
        interview.setTenantId(TenantContext.current());
        interview.setInvestigationId(investigationId);
        interview.setIntervieweeName(intervieweeName);
        interview.setIntervieweeRole(intervieweeRole);
        interview.setInterviewDate(interviewDate);
        interview.setSummary(summary);
        interview.setCreatedBy(currentRequest.username());

        ErInvestigationInterview saved = interviewRepo.save(interview);

        audit.record(MODULE, "ErInvestigationInterview", saved.getId().toString(), "ADDED", null, null);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ErInvestigationInterview> getInterviews(UUID investigationId) {
        ErInvestigation investigation = investigationRepo.findByIdAndTenantId(investigationId, TenantContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("Investigation not found: " + investigationId));

        ErCase erCase = get(investigation.getCaseId());
        checkConfidentialAccess(erCase);

        return interviewRepo.findByInvestigationIdOrderByInterviewDateAsc(investigationId);
    }

    @Transactional
    public ErEvidence addEvidence(UUID investigationId, String description, UUID attachmentId) {
        ErInvestigation investigation = investigationRepo.findByIdAndTenantId(investigationId, TenantContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("Investigation not found: " + investigationId));

        ErCase erCase = get(investigation.getCaseId());
        checkConfidentialAccess(erCase);

        ErEvidence evidence = new ErEvidence();
        evidence.setTenantId(TenantContext.current());
        evidence.setInvestigationId(investigationId);
        evidence.setDescription(description);
        evidence.setAttachmentId(attachmentId);
        evidence.setUploadedBy(currentRequest.username());

        ErEvidence saved = evidenceRepo.save(evidence);

        audit.record(MODULE, "ErEvidence", saved.getId().toString(), "ADDED", null, null);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ErEvidence> getEvidence(UUID investigationId) {
        ErInvestigation investigation = investigationRepo.findByIdAndTenantId(investigationId, TenantContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("Investigation not found: " + investigationId));

        ErCase erCase = get(investigation.getCaseId());
        checkConfidentialAccess(erCase);

        return evidenceRepo.findByInvestigationIdOrderByUploadedAtAsc(investigationId);
    }

    /**
     * Check if current user can access confidential case.
     * Only HR_ADMIN or owner can access.
     */
    private void checkConfidentialAccess(ErCase erCase) {
        if (erCase.getIsConfidential()) {
            boolean isAdmin = currentRequest.hasRole("HR_ADMIN");
            boolean isOwner = currentRequest.username().equals(erCase.getOwnerUsername());
            if (!isAdmin && !isOwner) {
                throw new ResourceNotFoundException("ER case not found: " + erCase.getId());
            }
        }
    }

    /**
     * Filter confidential cases from list.
     * Only HR_ADMIN or owner sees confidential cases.
     */
    private List<ErCase> filterConfidential(List<ErCase> cases) {
        boolean isAdmin = currentRequest.hasRole("HR_ADMIN");
        String username = currentRequest.username();

        return cases.stream()
                .filter(c -> !c.getIsConfidential() || isAdmin || username.equals(c.getOwnerUsername()))
                .collect(Collectors.toList());
    }
}
