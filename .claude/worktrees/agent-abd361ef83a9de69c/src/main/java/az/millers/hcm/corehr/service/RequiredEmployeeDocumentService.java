package az.millers.hcm.corehr.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.domain.DocumentRequirementType;
import az.millers.hcm.corehr.domain.RequiredDocumentStatus;
import az.millers.hcm.corehr.domain.RequiredEmployeeDocument;
import az.millers.hcm.corehr.repo.RequiredEmployeeDocumentRepository;
import az.millers.hcm.security.CurrentRequest;

/** M262 — Required employee document service (PRD §29). */
@Service
public class RequiredEmployeeDocumentService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "RequiredEmployeeDocument";

    private final RequiredEmployeeDocumentRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public RequiredEmployeeDocumentService(RequiredEmployeeDocumentRepository repo,
                                            AuditService audit,
                                            CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<RequiredEmployeeDocument> listForEmployee(UUID employeeId) {
        return repo.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public List<RequiredEmployeeDocument> listPending(UUID employeeId) {
        return repo.findByEmployeeIdAndStatusOrderByRequiredByDateAsc(
                employeeId, RequiredDocumentStatus.PENDING);
    }

    @Transactional
    public RequiredEmployeeDocument assign(UUID employeeId,
                                            DocumentRequirementType type,
                                            String label,
                                            LocalDate requiredByDate,
                                            String source,
                                            UUID sourceGrantId,
                                            String notes) {
        if (label == null || label.isBlank()) {
            throw new BadRequestException("label is required");
        }
        RequiredEmployeeDocument d = new RequiredEmployeeDocument();
        d.setEmployeeId(employeeId);
        d.setDocumentType(type);
        d.setLabel(label);
        d.setRequiredByDate(requiredByDate);
        d.setStatus(RequiredDocumentStatus.PENDING);
        d.setSource(source == null ? "MANUAL" : source);
        d.setSourceGrantId(sourceGrantId);
        d.setNotes(notes);
        d.setCreatedBy(currentRequest.username());
        d.setUpdatedBy(currentRequest.username());
        RequiredEmployeeDocument saved = repo.save(d);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, summarise(saved));
        return saved;
    }

    /** Operator (or self-service) marks a requirement as satisfied by linking an attachment. */
    @Transactional
    public Optional<RequiredEmployeeDocument> satisfy(UUID id, UUID attachmentId) {
        return repo.findById(id).map(d -> {
            if (d.getStatus() == RequiredDocumentStatus.SATISFIED) return d;
            d.setStatus(RequiredDocumentStatus.SATISFIED);
            d.setAttachmentId(attachmentId);
            d.setSatisfiedAt(OffsetDateTime.now());
            d.setSatisfiedBy(currentRequest.username());
            d.setUpdatedBy(currentRequest.username());
            RequiredEmployeeDocument saved = repo.save(d);
            audit.record(MODULE, ENTITY, saved.getId().toString(),
                    "SATISFY", null, summarise(saved));
            return saved;
        });
    }

    /** HR waives a requirement (e.g. internal transfer where the doc is already on file). */
    @Transactional
    public Optional<RequiredEmployeeDocument> waive(UUID id, String reason) {
        return repo.findById(id).map(d -> {
            if (d.getStatus().isTerminal()) return d;
            d.setStatus(RequiredDocumentStatus.WAIVED);
            if (reason != null && !reason.isBlank()) {
                d.setNotes((d.getNotes() == null ? "" : d.getNotes() + "\n") + "Waived: " + reason);
            }
            d.setUpdatedBy(currentRequest.username());
            RequiredEmployeeDocument saved = repo.save(d);
            audit.record(MODULE, ENTITY, saved.getId().toString(),
                    "WAIVE", null, summarise(saved));
            return saved;
        });
    }

    private java.util.Map<String, Object> summarise(RequiredEmployeeDocument d) {
        return java.util.Map.of(
                "employeeId", d.getEmployeeId(),
                "documentType", d.getDocumentType(),
                "label", d.getLabel(),
                "status", d.getStatus(),
                "requiredByDate", d.getRequiredByDate() == null ? "" : d.getRequiredByDate(),
                "source", d.getSource(),
                "sourceGrantId", d.getSourceGrantId() == null ? "" : d.getSourceGrantId());
    }
}
