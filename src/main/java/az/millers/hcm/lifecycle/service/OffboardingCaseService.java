package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.OffboardingCaseResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.OffboardingOverviewResponse;
import az.millers.hcm.lifecycle.domain.OffboardingCase;
import az.millers.hcm.lifecycle.domain.OffboardingCaseStatus;
import az.millers.hcm.lifecycle.domain.OffboardingSource;
import az.millers.hcm.lifecycle.domain.TerminationRequest;
import az.millers.hcm.lifecycle.event.OffboardingCaseActivatedEvent;
import az.millers.hcm.lifecycle.repo.OffboardingCaseRepository;
import az.millers.hcm.lifecycle.repo.TerminationRequestRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.common.BusinessNumbers;

@Service
public class OffboardingCaseService {

    private static final Logger log = LoggerFactory.getLogger(OffboardingCaseService.class);

    private static final List<OffboardingCaseStatus> ACTIVE_STATUSES = List.of(
            OffboardingCaseStatus.PENDING_APPROVAL, OffboardingCaseStatus.APPROVED,
            OffboardingCaseStatus.IN_PROGRESS, OffboardingCaseStatus.UNDER_NOTICE,
            OffboardingCaseStatus.CLEARANCE_PENDING, OffboardingCaseStatus.SETTLEMENT_PENDING);

    private final OffboardingCaseRepository cases;
    private final TerminationRequestRepository terminations;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final ApplicationEventPublisher events;

    public OffboardingCaseService(OffboardingCaseRepository cases,
                                   TerminationRequestRepository terminations,
                                   AuditService audit,
                                   CurrentRequest currentRequest,
                                   ApplicationEventPublisher events) {
        this.cases = cases;
        this.terminations = terminations;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public OffboardingCase get(UUID id) {
        return cases.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Offboarding case not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<OffboardingCase> list(UUID employeeId, OffboardingCaseStatus status, Pageable pageable) {
        if (employeeId != null) {
            return cases.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable);
        }
        if (status != null) {
            return cases.findByCaseStatusOrderByCreatedAtDesc(status, pageable);
        }
        return cases.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public OffboardingOverviewResponse overview() {
        long active = cases.countByCaseStatusIn(ACTIVE_STATUSES);
        long total = cases.count();

        LocalDate today = LocalDate.now();
        long leavingThisWeek = cases.findByLastWorkingDateBetweenAndCaseStatusIn(
                today, today.plusDays(7), ACTIVE_STATUSES).size();
        long leavingThisMonth = cases.findByLastWorkingDateBetweenAndCaseStatusIn(
                today, today.withDayOfMonth(today.lengthOfMonth()), ACTIVE_STATUSES).size();

        List<OffboardingCaseResponse> recent = cases
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10))
                .stream().map(OffboardingCaseResponse::from).toList();

        return new OffboardingOverviewResponse(active, leavingThisWeek, leavingThisMonth, total, recent);
    }

    @Transactional
    public OffboardingCase updateStatus(UUID id, OffboardingCaseStatus newStatus, String notes) {
        OffboardingCase c = get(id);
        OffboardingCaseResponse before = OffboardingCaseResponse.from(c);
        c.setCaseStatus(newStatus);
        if (notes != null && !notes.isBlank()) c.setNotes(notes);
        OffboardingCase saved = cases.save(c);
        audit.record("LIFECYCLE", "OffboardingCase", id.toString(),
                "STATUS_CHANGE", before, OffboardingCaseResponse.from(saved));
        return saved;
    }

    /** Called non-fatally from TerminationService.onApproved — idempotent. */
    @Transactional
    public void createFromTermination(UUID terminationId) {
        if (cases.findByTerminationId(terminationId).isPresent()) return;
        TerminationRequest t = terminations.findById(terminationId).orElse(null);
        if (t == null) return;

        OffboardingCase c = new OffboardingCase();
        c.setCaseNo(BusinessNumbers.format("OFB", 5, cases.nextNoSequence()));
        c.setEmployeeId(t.getEmployeeId());
        c.setSource(OffboardingSource.TERMINATION);
        c.setTerminationId(terminationId);
        c.setExitReason(t.getReasonCode() != null ? t.getReasonCode().name() : null);
        c.setLastWorkingDate(t.getLastWorkingDate());
        c.setCaseStatus(OffboardingCaseStatus.IN_PROGRESS);
        c.setCreatedBy("system");
        OffboardingCase saved = cases.save(c);
        events.publishEvent(new OffboardingCaseActivatedEvent(saved.getId(), saved.getEmployeeId()));
        log.info("Created offboarding case {} for termination {}", c.getCaseNo(), terminationId);
    }
}
