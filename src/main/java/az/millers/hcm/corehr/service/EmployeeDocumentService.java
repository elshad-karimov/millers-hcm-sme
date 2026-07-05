package az.millers.hcm.corehr.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.EmployeeDocumentRequest;
import az.millers.hcm.corehr.api.dto.EmployeeDocumentResponse;
import az.millers.hcm.corehr.domain.EmployeeDocument;
import az.millers.hcm.corehr.repo.EmployeeDocumentRepository;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * CRUD for {@link EmployeeDocument} (M169 / PRD §8.1.3).
 *
 * <p>Restricted documents ({@code restricted = true}) may only be created or
 * read by HR_ADMIN / SYSTEM_ADMIN; the controller enforces this at the
 * {@code @PreAuthorize} level — this service does not re-check roles.
 */
@Service
public class EmployeeDocumentService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeDocument";

    private final EmployeeDocumentRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;

    public EmployeeDocumentService(EmployeeDocumentRepository repository,
                                    EmployeeRepository employees,
                                    AuditService audit,
                                    AccessScopeService accessScope,
                                    CurrentRequest currentRequest) {
        this.repository = repository;
        this.employees = employees;
        this.audit = audit;
        this.accessScope = accessScope;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<EmployeeDocumentResponse> listFor(UUID employeeId) {
        ensureAccessible(employeeId);
        return repository.findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .stream()
                .map(EmployeeDocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeDocumentResponse get(UUID id) {
        EmployeeDocument d = findOrThrow(id);
        ensureAccessible(d.getEmployeeId());
        return EmployeeDocumentResponse.from(d);
    }

    @Transactional
    public EmployeeDocumentResponse create(UUID employeeId, EmployeeDocumentRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        EmployeeDocument d = new EmployeeDocument();
        d.setEmployeeId(employeeId);
        apply(d, req);
        d.setCreatedBy(currentRequest.username());
        d.setUpdatedBy(currentRequest.username());
        EmployeeDocument saved = repository.save(d);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, EmployeeDocumentResponse.from(saved));
        return EmployeeDocumentResponse.from(saved);
    }

    @Transactional
    public EmployeeDocumentResponse update(UUID id, EmployeeDocumentRequest req) {
        EmployeeDocument d = findOrThrow(id);
        EmployeeDocumentResponse before = EmployeeDocumentResponse.from(d);
        apply(d, req);
        d.setUpdatedBy(currentRequest.username());
        EmployeeDocument saved = repository.save(d);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, EmployeeDocumentResponse.from(saved));
        return EmployeeDocumentResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        EmployeeDocument d = findOrThrow(id);
        EmployeeDocumentResponse before = EmployeeDocumentResponse.from(d);
        repository.delete(d);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", before, null);
    }

    /**
     * M439 — Upload new version of an existing document.
     * Creates a new row with version+1, chaining previous_version_id.
     */
    @Transactional
    public EmployeeDocumentResponse uploadNewVersion(UUID documentId, UUID newAttachmentId, String notes) {
        EmployeeDocument old = findOrThrow(documentId);
        ensureAccessible(old.getEmployeeId());

        EmployeeDocument newVersion = new EmployeeDocument();
        newVersion.setEmployeeId(old.getEmployeeId());
        newVersion.setDocumentType(old.getDocumentType());
        newVersion.setCategoryId(old.getCategoryId());
        newVersion.setTitle(old.getTitle());
        newVersion.setExpiryDate(old.getExpiryDate());
        newVersion.setRestricted(old.isRestricted());
        newVersion.setAttachmentId(newAttachmentId);
        newVersion.setNotes(notes);
        newVersion.setVersion(old.getVersion() + 1);
        newVersion.setPreviousVersionId(documentId);
        newVersion.setCreatedBy(currentRequest.username());
        newVersion.setUpdatedBy(currentRequest.username());

        EmployeeDocument saved = repository.save(newVersion);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "UPLOAD_NEW_VERSION", null, EmployeeDocumentResponse.from(saved));
        return EmployeeDocumentResponse.from(saved);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void apply(EmployeeDocument d, EmployeeDocumentRequest req) {
        d.setDocumentType(req.documentType());
        d.setAttachmentId(req.attachmentId());
        d.setTitle(req.title());
        d.setExpiryDate(req.expiryDate());
        d.setRestricted(Boolean.TRUE.equals(req.restricted()));
        d.setNotes(req.notes());
    }

    private EmployeeDocument findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee document not found: " + id));
    }

    private void ensureAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }
}
