package az.millers.hcm.recruitment.service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.CandidateDocumentDtos.AddItemRequest;
import az.millers.hcm.recruitment.api.dto.CandidateDocumentDtos.Checklist;
import az.millers.hcm.recruitment.api.dto.CandidateDocumentDtos.ItemResponse;
import az.millers.hcm.recruitment.api.dto.CandidateDocumentDtos.StatusUpdate;
import az.millers.hcm.recruitment.api.dto.CandidateDocumentDtos.TypeRequest;
import az.millers.hcm.recruitment.api.dto.CandidateDocumentDtos.TypeResponse;
import az.millers.hcm.recruitment.domain.CandidateDocument;
import az.millers.hcm.recruitment.domain.DocumentStatus;
import az.millers.hcm.recruitment.domain.DocumentType;
import az.millers.hcm.recruitment.repo.CandidateDocumentRepository;
import az.millers.hcm.recruitment.repo.CandidateRepository;
import az.millers.hcm.recruitment.repo.DocumentTypeRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M292 — Recruitment PRD §12: candidate document checklist. Manages the
 * configurable document-type catalog and each candidate's per-document
 * checklist through PENDING → RECEIVED → VERIFIED (REJECTED / WAIVED).
 */
@Service
public class DocumentChecklistService {

    private static final String MODULE = "RECRUITMENT";

    private final DocumentTypeRepository types;
    private final CandidateDocumentRepository documents;
    private final CandidateRepository candidates;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public DocumentChecklistService(DocumentTypeRepository types,
                                     CandidateDocumentRepository documents,
                                     CandidateRepository candidates,
                                     AuditService audit,
                                     CurrentRequest currentRequest) {
        this.types = types;
        this.documents = documents;
        this.candidates = candidates;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Document-type catalog ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TypeResponse> listTypes(boolean activeOnly) {
        var list = activeOnly
                ? types.findByActiveTrueOrderByOrdinalAscNameAsc()
                : types.findAllByOrderByOrdinalAscNameAsc();
        return list.stream().map(TypeResponse::from).toList();
    }

    @Transactional
    public TypeResponse createType(TypeRequest req) {
        if (types.existsByCodeIgnoreCase(req.code().trim())) {
            throw new BadRequestException("Document type code already exists: " + req.code());
        }
        DocumentType t = new DocumentType();
        t.setCode(req.code().trim());
        applyType(t, req);
        t.setCreatedBy(currentRequest.username());
        t.setUpdatedBy(currentRequest.username());
        DocumentType saved = types.save(t);
        audit.record(MODULE, "DocumentType", saved.getId().toString(), "CREATE", null,
                Map.of("code", saved.getCode(), "name", saved.getName()));
        return TypeResponse.from(saved);
    }

    @Transactional
    public TypeResponse updateType(UUID id, TypeRequest req) {
        DocumentType t = types.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document type not found: " + id));
        applyType(t, req);
        t.setUpdatedBy(currentRequest.username());
        return TypeResponse.from(types.save(t));
    }

    private void applyType(DocumentType t, TypeRequest req) {
        t.setName(req.name());
        t.setDescription(req.description());
        t.setDefaultRequired(req.defaultRequired());
        t.setOrdinal(req.ordinal());
        if (req.active() != null) t.setActive(req.active());
    }

    // ── Per-candidate checklist ────────────────────────────────────────

    @Transactional(readOnly = true)
    public Checklist checklist(UUID candidateId) {
        requireCandidate(candidateId);
        Map<UUID, DocumentType> typeMap = typeMap();
        List<CandidateDocument> rows = documents.findByCandidateIdOrderByCreatedAtAsc(candidateId);
        List<ItemResponse> items = rows.stream()
                .map(d -> ItemResponse.from(d, typeMap.get(d.getDocumentTypeId())))
                .toList();
        int required = (int) rows.stream().filter(CandidateDocument::isRequired).count();
        int verified = (int) rows.stream()
                .filter(d -> d.getStatus() == DocumentStatus.VERIFIED).count();
        int outstanding = (int) rows.stream()
                .filter(CandidateDocument::isRequired)
                .filter(d -> d.getStatus() != DocumentStatus.VERIFIED
                        && d.getStatus() != DocumentStatus.WAIVED)
                .count();
        return new Checklist(items, rows.size(), required, verified, outstanding);
    }

    @Transactional
    public ItemResponse addItem(UUID candidateId, AddItemRequest req) {
        requireCandidate(candidateId);
        DocumentType type = types.findById(req.documentTypeId())
                .orElseThrow(() -> new BadRequestException(
                        "Document type not found: " + req.documentTypeId()));
        if (documents.existsByCandidateIdAndDocumentTypeId(candidateId, type.getId())) {
            throw new BadRequestException("That document is already on the checklist: " + type.getName());
        }
        CandidateDocument d = new CandidateDocument();
        d.setCandidateId(candidateId);
        d.setDocumentTypeId(type.getId());
        d.setRequired(req.required() != null ? req.required() : type.isDefaultRequired());
        d.setStatus(DocumentStatus.PENDING);
        CandidateDocument saved = documents.save(d);
        audit.record(MODULE, "CandidateDocument", saved.getId().toString(), "CREATE", null,
                Map.of("candidateId", candidateId.toString(), "type", type.getCode()));
        return ItemResponse.from(saved, type);
    }

    /**
     * Bootstrap a candidate's checklist with every active default-required
     * document type not already present. Returns the full checklist.
     */
    @Transactional
    public Checklist seedDefaults(UUID candidateId) {
        requireCandidate(candidateId);
        for (DocumentType t : types.findByActiveTrueAndDefaultRequiredTrueOrderByOrdinalAscNameAsc()) {
            if (!documents.existsByCandidateIdAndDocumentTypeId(candidateId, t.getId())) {
                CandidateDocument d = new CandidateDocument();
                d.setCandidateId(candidateId);
                d.setDocumentTypeId(t.getId());
                d.setRequired(true);
                d.setStatus(DocumentStatus.PENDING);
                documents.save(d);
            }
        }
        audit.record(MODULE, "CandidateDocument", candidateId.toString(), "SEED_DEFAULTS", null, null);
        return checklist(candidateId);
    }

    @Transactional
    public ItemResponse updateStatus(UUID id, StatusUpdate req) {
        CandidateDocument d = documents.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item not found: " + id));
        DocumentStatus from = d.getStatus();
        d.setStatus(req.status());
        if (req.notes() != null) d.setNotes(req.notes());
        if (req.required() != null) d.setRequired(req.required());

        OffsetDateTime now = OffsetDateTime.now();
        switch (req.status()) {
            case RECEIVED -> {
                if (d.getReceivedAt() == null) d.setReceivedAt(now);
                d.setVerifiedAt(null);
                d.setVerifiedBy(null);
            }
            case VERIFIED -> {
                if (d.getReceivedAt() == null) d.setReceivedAt(now);
                d.setVerifiedAt(now);
                d.setVerifiedBy(currentRequest.username());
            }
            case REJECTED -> {
                d.setVerifiedAt(null);
                d.setVerifiedBy(null);
            }
            case PENDING, WAIVED -> {
                d.setReceivedAt(null);
                d.setVerifiedAt(null);
                d.setVerifiedBy(null);
            }
        }
        CandidateDocument saved = documents.save(d);
        audit.record(MODULE, "CandidateDocument", id.toString(), "STATUS",
                Map.of("from", from.name()), Map.of("to", req.status().name()));
        DocumentType t = types.findById(d.getDocumentTypeId()).orElse(null);
        return ItemResponse.from(saved, t);
    }

    @Transactional
    public void deleteItem(UUID id) {
        documents.deleteById(id);
        audit.record(MODULE, "CandidateDocument", id.toString(), "DELETE", null, null);
    }

    private void requireCandidate(UUID candidateId) {
        if (!candidates.existsById(candidateId)) {
            throw new ResourceNotFoundException("Candidate not found: " + candidateId);
        }
    }

    private Map<UUID, DocumentType> typeMap() {
        Map<UUID, DocumentType> m = new HashMap<>();
        for (DocumentType t : types.findAll()) m.put(t.getId(), t);
        return m;
    }
}
