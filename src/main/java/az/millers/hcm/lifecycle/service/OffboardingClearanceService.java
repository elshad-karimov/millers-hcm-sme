package az.millers.hcm.lifecycle.service;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.ClearanceResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.ClearanceSignOffRequest;
import az.millers.hcm.lifecycle.domain.ClearanceDepartment;
import az.millers.hcm.lifecycle.domain.ClearanceStatus;
import az.millers.hcm.lifecycle.domain.OffboardingClearance;
import az.millers.hcm.lifecycle.event.OffboardingCaseActivatedEvent;
import az.millers.hcm.lifecycle.repo.OffboardingClearanceRepository;

@Service
public class OffboardingClearanceService {

    private static final Logger log = LoggerFactory.getLogger(OffboardingClearanceService.class);

    private static final List<ClearanceStatus> RESOLVED_STATUSES =
            List.of(ClearanceStatus.CLEARED, ClearanceStatus.CLEARED_WITH_DEDUCTION, ClearanceStatus.WAIVED);

    private final OffboardingClearanceRepository repo;

    public OffboardingClearanceService(OffboardingClearanceRepository repo) {
        this.repo = repo;
    }

    /** Auto-seeds one clearance row per department when a case becomes IN_PROGRESS. */
    @EventListener
    @Transactional
    public void onCaseActivated(OffboardingCaseActivatedEvent event) {
        try {
            for (ClearanceDepartment dept : ClearanceDepartment.values()) {
                repo.findByCaseIdAndDepartment(event.caseId(), dept).ifPresentOrElse(
                        existing -> log.debug("Clearance row already exists for case {} dept {}", event.caseId(), dept),
                        () -> {
                            OffboardingClearance row = new OffboardingClearance();
                            row.setCaseId(event.caseId());
                            row.setDepartment(dept);
                            row.setStatus(ClearanceStatus.NOT_STARTED);
                            repo.save(row);
                        });
            }
            log.info("Seeded {} clearance departments for offboarding case {}", ClearanceDepartment.values().length, event.caseId());
        } catch (Exception e) {
            log.warn("Failed to seed clearance rows for case {}: {}", event.caseId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ClearanceResponse> getClearances(UUID caseId) {
        return repo.findByCaseIdOrderByDepartment(caseId)
                .stream().map(ClearanceResponse::from).toList();
    }

    @Transactional
    public ClearanceResponse signOff(UUID caseId, ClearanceDepartment department, ClearanceSignOffRequest request) {
        OffboardingClearance row = repo.findByCaseIdAndDepartment(caseId, department)
                .orElseThrow(() -> new IllegalStateException("No clearance row found for dept " + department));

        row.setStatus(request.status());
        row.setDeductionAmount(request.deductionAmount());
        row.setNotes(request.notes());

        if (Arrays.asList(ClearanceStatus.CLEARED, ClearanceStatus.CLEARED_WITH_DEDUCTION, ClearanceStatus.WAIVED)
                .contains(request.status())) {
            row.setClearedAt(OffsetDateTime.now());
            try {
                row.setClearedBy(SecurityContextHolder.getContext().getAuthentication().getName());
            } catch (Exception ignored) {}
        }

        return ClearanceResponse.from(repo.save(row));
    }

    @Transactional(readOnly = true)
    public boolean isFullyCleared(UUID caseId) {
        return !repo.existsByCaseIdAndStatusNotIn(caseId, RESOLVED_STATUSES);
    }
}
