package az.millers.hcm.compliance.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compliance.domain.PrivacyRequest;
import az.millers.hcm.compliance.domain.PrivacyRequest.RequestStatus;
import az.millers.hcm.compliance.repo.PrivacyRequestRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M472 — Privacy request management (GDPR compliance).
 */
@Service
public class PrivacyRequestService {

    private static final String MODULE = "COMPLIANCE";
    private static final String ENTITY = "PrivacyRequest";

    private final PrivacyRequestRepository requests;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PrivacyRequestService(PrivacyRequestRepository requests,
                                  AuditService audit,
                                  CurrentRequest currentRequest) {
        this.requests = requests;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<PrivacyRequest> list() {
        return requests.findByTenantIdOrderByCreatedAtDesc("default");
    }

    @Transactional(readOnly = true)
    public PrivacyRequest get(UUID id) {
        PrivacyRequest request = requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Privacy request not found: " + id));

        if (!"default".equals(request.getTenantId())) {
            throw new ResourceNotFoundException("Privacy request not found: " + id);
        }

        return request;
    }

    @Transactional
    public PrivacyRequest create(PrivacyRequest request) {
        request.setTenantId("default");
        request.setCreatedBy(currentRequest.username());
        request.setUpdatedBy(currentRequest.username());

        requests.save(request);

        audit.record(MODULE, ENTITY, request.getId().toString(), "CREATED",
                null, request.getRequestType().name());

        return request;
    }

    @Transactional
    public PrivacyRequest update(UUID id, PrivacyRequest update) {
        PrivacyRequest existing = get(id);

        existing.setEmployeeId(update.getEmployeeId());
        existing.setRequestType(update.getRequestType());
        existing.setDescription(update.getDescription());
        existing.setStatus(update.getStatus());
        existing.setDueDate(update.getDueDate());
        existing.setResolutionNotes(update.getResolutionNotes());
        existing.setUpdatedBy(currentRequest.username());
        existing.setUpdatedAt(OffsetDateTime.now());

        requests.save(existing);

        audit.record(MODULE, ENTITY, id.toString(), "UPDATED", null, update.getStatus().name());

        return existing;
    }

    @Transactional
    public PrivacyRequest updateStatus(UUID id, RequestStatus newStatus, String resolutionNotes) {
        PrivacyRequest request = get(id);

        RequestStatus oldStatus = request.getStatus();
        request.setStatus(newStatus);
        request.setResolutionNotes(resolutionNotes);
        request.setUpdatedBy(currentRequest.username());
        request.setUpdatedAt(OffsetDateTime.now());

        requests.save(request);

        audit.record(MODULE, ENTITY, id.toString(), "STATUS_CHANGED",
                oldStatus.name(), newStatus.name());

        return request;
    }

    @Transactional
    public void delete(UUID id) {
        PrivacyRequest request = get(id);
        requests.delete(request);

        audit.record(MODULE, ENTITY, id.toString(), "DELETED", null, null);
    }
}
