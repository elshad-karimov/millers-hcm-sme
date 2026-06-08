package az.millers.hcm.organization.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.organization.api.dto.OrgUnitDocumentDtos.OrgUnitDocumentRequest;
import az.millers.hcm.organization.api.dto.OrgUnitDocumentDtos.OrgUnitDocumentResponse;
import az.millers.hcm.organization.domain.OrgUnit;
import az.millers.hcm.organization.domain.OrgUnitDocument;
import az.millers.hcm.organization.repo.OrgUnitDocumentRepository;
import az.millers.hcm.organization.repo.OrgUnitRepository;

/**
 * M147 / §31 — org-unit document registry.
 *
 * <p>Expiry tracking is handled automatically by the shared
 * {@link az.millers.hcm.common.expiry.ExpiryAlertScheduler} via
 * {@link OrgUnitDocumentExpirySource} — no additional wiring needed here.
 */
@Service
public class OrgUnitDocumentService {

    private final OrgUnitDocumentRepository documents;
    private final OrgUnitRepository orgUnits;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public OrgUnitDocumentService(OrgUnitDocumentRepository documents,
                                   OrgUnitRepository orgUnits,
                                   AuditService audit,
                                   CurrentRequest currentRequest) {
        this.documents = documents;
        this.orgUnits = orgUnits;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<OrgUnitDocumentResponse> list(UUID orgUnitId) {
        requireUnit(orgUnitId);
        return documents.findByOrgUnitIdOrderByCreatedAtDesc(orgUnitId)
                .stream().map(OrgUnitDocumentResponse::from).toList();
    }

    @Transactional
    public OrgUnitDocumentResponse create(UUID orgUnitId, OrgUnitDocumentRequest req) {
        requireUnit(orgUnitId);
        OrgUnitDocument doc = new OrgUnitDocument();
        doc.setOrgUnitId(orgUnitId);
        applyRequest(doc, req);
        doc.setCreatedBy(currentRequest.username());
        doc.setUpdatedBy(currentRequest.username());
        OrgUnitDocument saved = documents.save(doc);
        audit.record("ORGANIZATION", "OrgUnitDocument", saved.getId().toString(),
                "CREATE", null, OrgUnitDocumentResponse.from(saved));
        return OrgUnitDocumentResponse.from(saved);
    }

    @Transactional
    public OrgUnitDocumentResponse update(UUID orgUnitId, UUID docId, OrgUnitDocumentRequest req) {
        OrgUnitDocument doc = get(orgUnitId, docId);
        OrgUnitDocumentResponse before = OrgUnitDocumentResponse.from(doc);
        applyRequest(doc, req);
        doc.setUpdatedBy(currentRequest.username());
        OrgUnitDocument saved = documents.save(doc);
        audit.record("ORGANIZATION", "OrgUnitDocument", saved.getId().toString(),
                "UPDATE", before, OrgUnitDocumentResponse.from(saved));
        return OrgUnitDocumentResponse.from(saved);
    }

    @Transactional
    public void delete(UUID orgUnitId, UUID docId) {
        OrgUnitDocument doc = get(orgUnitId, docId);
        documents.delete(doc);
        audit.record("ORGANIZATION", "OrgUnitDocument", docId.toString(),
                "DELETE", OrgUnitDocumentResponse.from(doc), null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private OrgUnitDocument get(UUID orgUnitId, UUID docId) {
        OrgUnitDocument doc = documents.findById(docId)
                .orElseThrow(() -> new BadRequestException("Document not found: " + docId));
        if (!doc.getOrgUnitId().equals(orgUnitId)) {
            throw new BadRequestException("Document does not belong to org unit " + orgUnitId);
        }
        return doc;
    }

    private OrgUnit requireUnit(UUID orgUnitId) {
        return orgUnits.findById(orgUnitId)
                .orElseThrow(() -> new BadRequestException("Org unit not found: " + orgUnitId));
    }

    private void applyRequest(OrgUnitDocument doc, OrgUnitDocumentRequest req) {
        doc.setTitle(req.title());
        doc.setDocType(req.docType());
        doc.setDocumentRef(req.documentRef());
        doc.setIssuedDate(req.issuedDate());
        doc.setExpiryDate(req.expiryDate());
        doc.setResponsibleEmployeeId(req.responsibleEmployeeId());
        doc.setNotes(req.notes());
    }
}
