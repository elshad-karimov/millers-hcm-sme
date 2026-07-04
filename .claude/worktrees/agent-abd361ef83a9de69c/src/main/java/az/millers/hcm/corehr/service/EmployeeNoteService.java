package az.millers.hcm.corehr.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.NoteRequest;
import az.millers.hcm.corehr.api.dto.NoteResponse;
import az.millers.hcm.corehr.domain.EmployeeNote;
import az.millers.hcm.corehr.domain.NoteType;
import az.millers.hcm.corehr.domain.NoteVisibility;
import az.millers.hcm.corehr.repo.EmployeeNoteRepository;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * CRUD for {@link EmployeeNote} with per-row visibility filtering (M72 / P2-10).
 *
 * <p>List requests pull every note for the employee from the repo then drop
 * the ones whose {@link NoteVisibility#isVisibleToCurrentCaller()} returns
 * false. This is intentionally application-side — the role-based check
 * cannot be pushed into a single SQL predicate without a tenant-specific
 * role table.
 *
 * <p>Default visibility on create follows the note type:
 * <ul>
 *   <li>SYSTEM       → SYSTEM_ADMIN_ONLY</li>
 *   <li>HR / PAYROLL / CONFIDENTIAL → HR_ONLY</li>
 *   <li>MANAGER      → MANAGER_ONLY</li>
 *   <li>GENERAL / PERFORMANCE → ALL_HR</li>
 * </ul>
 * Callers can override by setting {@code visibilityLevel} explicitly on the
 * request.
 */
@Service
public class EmployeeNoteService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeNote";

    private final EmployeeNoteRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;

    public EmployeeNoteService(EmployeeNoteRepository repository,
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
    public List<NoteResponse> listFor(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        return repository.findByEmployeeIdOrderByPinnedDescCreatedAtDesc(employeeId)
                .stream()
                .filter(n -> n.getVisibilityLevel().isVisibleToCurrentCaller())
                .map(NoteResponse::from)
                .toList();
    }

    @Transactional
    public NoteResponse create(UUID employeeId, NoteRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        EmployeeNote n = new EmployeeNote();
        n.setEmployeeId(employeeId);
        apply(n, req);
        n.setCreatedBy(currentRequest.username());
        n.setUpdatedBy(currentRequest.username());
        EmployeeNote saved = repository.save(n);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, NoteResponse.from(saved));
        return NoteResponse.from(saved);
    }

    @Transactional
    public NoteResponse update(UUID id, NoteRequest req) {
        EmployeeNote n = loadOrThrow(id);
        // Visibility check on update — you can't edit a note you couldn't read.
        if (!n.getVisibilityLevel().isVisibleToCurrentCaller()) {
            throw new ResourceNotFoundException("Note not found: " + id);
        }
        NoteResponse before = NoteResponse.from(n);
        apply(n, req);
        n.setUpdatedBy(currentRequest.username());
        EmployeeNote saved = repository.save(n);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, NoteResponse.from(saved));
        return NoteResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        EmployeeNote n = loadOrThrow(id);
        if (!n.getVisibilityLevel().isVisibleToCurrentCaller()) {
            throw new ResourceNotFoundException("Note not found: " + id);
        }
        NoteResponse before = NoteResponse.from(n);
        repository.delete(n);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", before, null);
    }

    private void apply(EmployeeNote n, NoteRequest req) {
        NoteType type = req.noteType() != null ? req.noteType() : NoteType.GENERAL;
        n.setNoteType(type);
        n.setNoteBody(req.noteBody());
        // Caller-supplied visibility wins; otherwise derive a sensible default
        // from the note type.
        n.setVisibilityLevel(req.visibilityLevel() != null
                ? req.visibilityLevel()
                : defaultVisibilityFor(type));
        n.setPinned(Boolean.TRUE.equals(req.pinned()));
    }

    private NoteVisibility defaultVisibilityFor(NoteType type) {
        return switch (type) {
            case SYSTEM                              -> NoteVisibility.SYSTEM_ADMIN_ONLY;
            case HR, PAYROLL, CONFIDENTIAL           -> NoteVisibility.HR_ONLY;
            case MANAGER                             -> NoteVisibility.MANAGER_ONLY;
            case GENERAL, PERFORMANCE                -> NoteVisibility.ALL_HR;
        };
    }

    private EmployeeNote loadOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Note not found: " + id));
    }

    private void ensureEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }
}
