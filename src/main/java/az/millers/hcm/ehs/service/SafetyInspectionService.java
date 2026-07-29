package az.millers.hcm.ehs.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.ehs.domain.FindingStatus;
import az.millers.hcm.ehs.domain.InspectionFinding;
import az.millers.hcm.ehs.domain.InspectionStatus;
import az.millers.hcm.ehs.domain.SafetyInspection;
import az.millers.hcm.ehs.repo.InspectionFindingRepository;
import az.millers.hcm.ehs.repo.SafetyInspectionRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M450 — Safety inspection service.
 * Complete computes overall_score = % OK findings.
 */
@Service
public class SafetyInspectionService {

    private static final String MODULE = "ehs";
    private static final String ENTITY = "SafetyInspection";

    private final SafetyInspectionRepository repo;
    private final InspectionFindingRepository findingRepo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public SafetyInspectionService(SafetyInspectionRepository repo,
                                   InspectionFindingRepository findingRepo,
                                   AuditService audit,
                                   CurrentRequest currentRequest) {
        this.repo = repo;
        this.findingRepo = findingRepo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public SafetyInspection create(UUID workLocationId,
                                    LocalDate inspectionDate,
                                    String inspectorUsername,
                                    String title,
                                    String notes,
                                    List<FindingInput> findings) {

        SafetyInspection inspection = new SafetyInspection();
        inspection.setTenantId(TenantContext.current());
        inspection.setWorkLocationId(workLocationId);
        inspection.setInspectionDate(inspectionDate);
        inspection.setInspectorUsername(inspectorUsername);
        inspection.setTitle(title);
        inspection.setNotes(notes);
        inspection.setStatus(InspectionStatus.SCHEDULED);
        inspection.setCreatedBy(currentRequest.username());
        inspection.setUpdatedBy(currentRequest.username());

        SafetyInspection saved = repo.save(inspection);

        // Save findings
        if (findings != null) {
            for (FindingInput f : findings) {
                InspectionFinding finding = new InspectionFinding();
                finding.setInspectionId(saved.getId());
                finding.setItemLabel(f.itemLabel());
                finding.setFindingStatus(f.findingStatus());
                finding.setNotes(f.notes());
                finding.setCorrectiveActionId(f.correctiveActionId());
                findingRepo.save(finding);
            }
        }

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATED", null, null);

        return saved;
    }

    @Transactional
    public SafetyInspection update(UUID id,
                                    String title,
                                    String notes,
                                    List<FindingInput> findings) {

        SafetyInspection inspection = get(id);

        if (title != null) inspection.setTitle(title);
        if (notes != null) inspection.setNotes(notes);

        inspection.setUpdatedBy(currentRequest.username());
        inspection.setUpdatedAt(OffsetDateTime.now());

        SafetyInspection updated = repo.save(inspection);

        // Update findings if provided
        if (findings != null) {
            findingRepo.deleteByInspectionId(id);
            for (FindingInput f : findings) {
                InspectionFinding finding = new InspectionFinding();
                finding.setInspectionId(id);
                finding.setItemLabel(f.itemLabel());
                finding.setFindingStatus(f.findingStatus());
                finding.setNotes(f.notes());
                finding.setCorrectiveActionId(f.correctiveActionId());
                findingRepo.save(finding);
            }
        }

        audit.record(MODULE, ENTITY, id.toString(), "UPDATED", null, null);

        return updated;
    }

    @Transactional
    public void complete(UUID id) {
        SafetyInspection inspection = get(id);

        // Compute overall_score = % OK
        List<InspectionFinding> findings = findingRepo.findByInspectionId(id);
        if (!findings.isEmpty()) {
            long okCount = findings.stream()
                    .filter(f -> f.getFindingStatus() == FindingStatus.OK)
                    .count();
            int overallScore = (int) ((okCount * 100) / findings.size());
            inspection.setOverallScore(overallScore);
        } else {
            inspection.setOverallScore(null); // No findings
        }

        inspection.setStatus(InspectionStatus.COMPLETED);
        inspection.setUpdatedBy(currentRequest.username());
        inspection.setUpdatedAt(OffsetDateTime.now());

        repo.save(inspection);

        audit.record(MODULE, ENTITY, id.toString(), "COMPLETED",
                Map.of("status", InspectionStatus.SCHEDULED),
                Map.of("status", InspectionStatus.COMPLETED));
    }

    @Transactional(readOnly = true)
    public SafetyInspection get(UUID id) {
        SafetyInspection inspection = repo.findByIdAndTenantId(id, TenantContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("Safety inspection not found: " + id));

        // Tenant post-check
        if (!TenantContext.current().equals(inspection.getTenantId())) {
            throw new ResourceNotFoundException("Safety inspection not found: " + id);
        }

        return inspection;
    }

    @Transactional(readOnly = true)
    public List<SafetyInspection> list(InspectionStatus statusFilter) {
        if (statusFilter != null) {
            return repo.findByTenantIdAndStatusOrderByInspectionDateDesc(TenantContext.current(), statusFilter);
        } else {
            return repo.findByTenantIdOrderByInspectionDateDesc(TenantContext.current());
        }
    }

    @Transactional(readOnly = true)
    public List<InspectionFinding> getFindings(UUID inspectionId) {
        // Verify inspection exists and tenant matches
        get(inspectionId);
        return findingRepo.findByInspectionId(inspectionId);
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record FindingInput(String itemLabel, FindingStatus findingStatus, String notes, UUID correctiveActionId) {}
}
