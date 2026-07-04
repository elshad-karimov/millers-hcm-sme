package az.millers.hcm.lifecycle.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.ItAccessResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.ItAccessUpdateRequest;
import az.millers.hcm.lifecycle.domain.ItAccessStatus;
import az.millers.hcm.lifecycle.domain.ItAccessType;
import az.millers.hcm.lifecycle.domain.OffboardingItAccess;
import az.millers.hcm.lifecycle.event.OffboardingCaseActivatedEvent;
import az.millers.hcm.lifecycle.repo.OffboardingItAccessRepository;

@Service
public class OffboardingItAccessService {

    private static final Logger log = LoggerFactory.getLogger(OffboardingItAccessService.class);

    private static final List<ItAccessStatus> TERMINAL_STATUSES = List.of(
            ItAccessStatus.REMOVED, ItAccessStatus.NOT_APPLICABLE, ItAccessStatus.WAIVED);

    private final OffboardingItAccessRepository repo;

    public OffboardingItAccessService(OffboardingItAccessRepository repo) {
        this.repo = repo;
    }

    @EventListener
    @Transactional
    public void onCaseActivated(OffboardingCaseActivatedEvent event) {
        try {
            for (ItAccessType type : ItAccessType.values()) {
                repo.findByCaseIdAndAccessType(event.caseId(), type.name()).ifPresentOrElse(
                        existing -> {},
                        () -> {
                            OffboardingItAccess row = new OffboardingItAccess();
                            row.setCaseId(event.caseId());
                            row.setAccessType(type.name());
                            row.setDisplayLabel(type.getLabel());
                            row.setAccessStatus(ItAccessStatus.PENDING);
                            repo.save(row);
                        });
            }
            log.info("Seeded {} IT access rows for case {}", ItAccessType.values().length, event.caseId());
        } catch (Exception e) {
            log.warn("Failed to seed IT access rows for case {}: {}", event.caseId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ItAccessResponse> getAccess(UUID caseId) {
        return repo.findByCaseIdOrderByAccessType(caseId).stream().map(ItAccessResponse::from).toList();
    }

    @Transactional
    public ItAccessResponse update(UUID accessId, ItAccessUpdateRequest request) {
        OffboardingItAccess row = repo.findById(accessId)
                .orElseThrow(() -> new ResourceNotFoundException("IT access row not found: " + accessId));
        if (request.accessStatus() != null) row.setAccessStatus(request.accessStatus());
        if (request.removalDate() != null) row.setRemovalDate(request.removalDate());
        if (request.handledBy() != null) row.setHandledBy(request.handledBy());
        if (request.reference() != null) row.setReference(request.reference());
        if (request.notes() != null) row.setNotes(request.notes());
        return ItAccessResponse.from(repo.save(row));
    }

    @Transactional(readOnly = true)
    public boolean isFullyRemoved(UUID caseId) {
        return !repo.existsByCaseIdAndAccessStatusNotIn(caseId, TERMINAL_STATUSES);
    }
}
