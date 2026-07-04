package az.millers.hcm.learning.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.learning.domain.EmployeeCompetency;
import az.millers.hcm.learning.domain.SkillVerificationRequest;
import az.millers.hcm.learning.domain.SkillVerificationRequest.VerificationStatus;
import az.millers.hcm.learning.repo.EmployeeCompetencyRepository;
import az.millers.hcm.learning.repo.SkillVerificationRequestRepository;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * M419: Skill verification workflow — employee submits request, manager/HR approves.
 */
@Service
public class SkillVerificationService {

    private static final String TENANT_ID = "default";
    private static final String MODULE = "LEARNING";
    private static final String ENTITY = "SkillVerification";

    private final SkillVerificationRequestRepository requests;
    private final EmployeeCompetencyRepository employeeCompetencies;
    private final AccessScopeService accessScope;
    private final EmployeeContextService employeeContext;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;

    public SkillVerificationService(SkillVerificationRequestRepository requests,
                                     EmployeeCompetencyRepository employeeCompetencies,
                                     AccessScopeService accessScope,
                                     EmployeeContextService employeeContext,
                                     AuditService audit,
                                     NamedParameterJdbcTemplate jdbc) {
        this.requests = requests;
        this.employeeCompetencies = employeeCompetencies;
        this.accessScope = accessScope;
        this.employeeContext = employeeContext;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional
    public SkillVerificationRequest submitRequest(UUID competencyId, int requestedLevel, String evidenceNotes) {
        if (requestedLevel < 1 || requestedLevel > 5) {
            throw new BadRequestException("Requested level must be 1-5");
        }
        UUID employeeId = employeeContext.currentEmployee().getId();
        if (!accessScope.isAccessible(employeeId)) {
            throw new BadRequestException("Cannot submit verification for another employee");
        }

        SkillVerificationRequest req = new SkillVerificationRequest();
        req.setTenantId(TENANT_ID);
        req.setEmployeeId(employeeId);
        req.setCompetencyId(competencyId);
        req.setRequestedLevel(requestedLevel);
        req.setEvidenceNotes(evidenceNotes);
        req.setStatus(VerificationStatus.PENDING);

        SkillVerificationRequest saved = requests.save(req);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "SUBMIT", null, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SkillVerificationRequest> getPendingRequests() {
        return requests.findByTenantIdAndStatusOrderByCreatedAtDesc(TENANT_ID, VerificationStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<SkillVerificationRequest> getMyRequests() {
        UUID employeeId = employeeContext.currentEmployee().getId();
        return requests.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TENANT_ID, employeeId);
    }

    @Transactional
    public SkillVerificationRequest approve(UUID requestId, String verificationNotes) {
        SkillVerificationRequest req = requests.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

        if (!req.getTenantId().equals(TENANT_ID)) {
            throw new ResourceNotFoundException("Request not found");
        }
        if (req.getStatus() != VerificationStatus.PENDING) {
            throw new BadRequestException("Request is not pending");
        }

        // Check access: manager-of-employee or HR
        if (!canVerify(req.getEmployeeId())) {
            throw new BadRequestException("Not authorized to verify this request");
        }

        UUID verifierId = employeeContext.currentEmployee().getId();
        req.setVerifiedByEmployeeId(verifierId);
        req.setVerifiedAt(OffsetDateTime.now());
        req.setVerificationNotes(verificationNotes);
        req.setStatus(VerificationStatus.APPROVED);

        SkillVerificationRequest saved = requests.save(req);

        // Upsert employee competency
        upsertCompetency(req.getEmployeeId(), req.getCompetencyId(), req.getRequestedLevel(), verifierId);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "APPROVE", null, saved);
        return saved;
    }

    @Transactional
    public SkillVerificationRequest reject(UUID requestId, String verificationNotes) {
        SkillVerificationRequest req = requests.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

        if (!req.getTenantId().equals(TENANT_ID)) {
            throw new ResourceNotFoundException("Request not found");
        }
        if (req.getStatus() != VerificationStatus.PENDING) {
            throw new BadRequestException("Request is not pending");
        }

        if (!canVerify(req.getEmployeeId())) {
            throw new BadRequestException("Not authorized to verify this request");
        }

        UUID verifierId = employeeContext.currentEmployee().getId();
        req.setVerifiedByEmployeeId(verifierId);
        req.setVerifiedAt(OffsetDateTime.now());
        req.setVerificationNotes(verificationNotes);
        req.setStatus(VerificationStatus.REJECTED);

        SkillVerificationRequest saved = requests.save(req);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "REJECT", null, saved);
        return saved;
    }

    private boolean canVerify(UUID employeeId) {
        // HR can verify any
        if (accessScope.isUnrestricted()) {
            return true;
        }
        // Manager can verify own reports
        return accessScope.isAccessible(employeeId);
    }

    private void upsertCompetency(UUID employeeId, UUID competencyId, int level, UUID verifierId) {
        // Find existing competency
        Optional<EmployeeCompetency> existing = employeeCompetencies
                .findByEmployeeIdOrderByAwardedAtDesc(employeeId)
                .stream()
                .filter(ec -> ec.getCompetencyId().equals(competencyId))
                .findFirst();

        if (existing.isPresent()) {
            // Update existing
            EmployeeCompetency ec = existing.get();
            ec.setProficiency(level);
            ec.setEndorsedByEmployeeId(verifierId);
            ec.setEndorsedAt(OffsetDateTime.now());
            ec.setEndorsedLevel(level);
            employeeCompetencies.save(ec);
        } else {
            // Create new
            EmployeeCompetency ec = new EmployeeCompetency();
            ec.setEmployeeId(employeeId);
            ec.setCompetencyId(competencyId);
            ec.setProficiency(level);
            ec.setSource("MANUAL");
            ec.setEndorsedByEmployeeId(verifierId);
            ec.setEndorsedAt(OffsetDateTime.now());
            ec.setEndorsedLevel(level);
            employeeCompetencies.save(ec);
        }
    }
}
