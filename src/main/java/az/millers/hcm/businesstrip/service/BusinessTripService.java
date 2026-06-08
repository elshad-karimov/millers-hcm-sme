package az.millers.hcm.businesstrip.service;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.businesstrip.api.dto.BusinessTripResponse;
import az.millers.hcm.businesstrip.api.dto.BusinessTripSubmitRequest;
import az.millers.hcm.businesstrip.api.dto.ExpenseReconcileRequest;
import az.millers.hcm.businesstrip.domain.BusinessTripRequest;
import az.millers.hcm.businesstrip.domain.TripStatus;
import az.millers.hcm.businesstrip.event.BusinessTripApprovedEvent;
import az.millers.hcm.businesstrip.repo.BusinessTripRequestRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.service.WorkflowService;

@Service
public class BusinessTripService {

    public static final String WORKFLOW_DEFINITION = "BUSINESS_TRIP_APPROVAL";
    private static final String MODULE = "BUSINESS_TRIP";
    private static final String ENTITY = "BusinessTripRequest";

    private final BusinessTripRequestRepository trips;
    private final EmployeeRepository employees;
    private final WorkflowService workflowService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final AccessScopeService accessScope;
    private final ApplicationEventPublisher eventPublisher;

    public BusinessTripService(BusinessTripRequestRepository trips,
                                EmployeeRepository employees,
                                WorkflowService workflowService,
                                AuditService audit,
                                CurrentRequest currentRequest,
                                AccessScopeService accessScope,
                                ApplicationEventPublisher eventPublisher) {
        this.trips = trips;
        this.employees = employees;
        this.workflowService = workflowService;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.accessScope = accessScope;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public BusinessTripRequest get(UUID id) {
        BusinessTripRequest t = trips.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Business trip not found: " + id));
        // ABAC: hide rows the caller isn't scoped to behind a 404 (PRD 14.9).
        if (!accessScope.isAccessible(t.getEmployeeId())) {
            throw new ResourceNotFoundException("Business trip not found: " + id);
        }
        return t;
    }

    @Transactional(readOnly = true)
    public Page<BusinessTripRequest> list(UUID employeeId, TripStatus status, Pageable pageable) {
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope == null) {
            if (employeeId != null) return trips.findByEmployeeIdOrderByStartDateDesc(employeeId, pageable);
            if (status != null) return trips.findByStatusOrderByStartDateDesc(status, pageable);
            return trips.findAllByOrderByStartDateDesc(pageable);
        }
        if (scope.isEmpty()) return Page.empty(pageable);
        if (employeeId != null) {
            if (!scope.contains(employeeId)) return Page.empty(pageable);
            return trips.findByEmployeeIdOrderByStartDateDesc(employeeId, pageable);
        }
        if (status != null) {
            return trips.findByEmployeeIdInAndStatusOrderByStartDateDesc(scope, status, pageable);
        }
        return trips.findByEmployeeIdInOrderByStartDateDesc(scope, pageable);
    }

    @Transactional
    public BusinessTripRequest submit(BusinessTripSubmitRequest req) {
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        if (req.startDate().isAfter(req.endDate())) {
            throw new BadRequestException("startDate must be on or before endDate");
        }

        BusinessTripRequest t = new BusinessTripRequest();
        t.setTripNo(String.format("BT-%05d", trips.nextTripNoSequence()));
        t.setEmployeeId(req.employeeId());
        t.setTripType(req.tripType());
        t.setDestinationCountry(req.destinationCountry());
        t.setDestinationCity(req.destinationCity());
        t.setPurpose(req.purpose());
        t.setProject(req.project());
        t.setCostCentre(req.costCentre());
        t.setStartDate(req.startDate());
        t.setEndDate(req.endDate());
        t.setTotalDays((int) ChronoUnit.DAYS.between(req.startDate(), req.endDate()) + 1);
        t.setCurrency(StringUtils.hasText(req.currency()) ? req.currency().toUpperCase() : "AZN");
        t.setDailyAllowance(req.dailyAllowance());
        t.setRequestedAdvance(req.requestedAdvance());
        t.setMealsProvided(Boolean.TRUE.equals(req.mealsProvided()));
        t.setAccommodationProvided(Boolean.TRUE.equals(req.accommodationProvided()));
        t.setAttachmentUrls(req.attachmentUrls());
        t.setStatus(TripStatus.PENDING);
        t.setCreatedBy(currentRequest.username());
        BusinessTripRequest saved = trips.save(t);

        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY,
                saved.getId().toString(),
                saved.getTripNo() + " — " + saved.getDestinationCity()
                        + " (" + saved.getTotalDays() + " day" + (saved.getTotalDays() == 1 ? "" : "s")
                        + (saved.getRequestedAdvance() != null
                                ? ", advance " + saved.getRequestedAdvance() + " " + saved.getCurrency()
                                : "") + ")",
                Map.of(
                        "destination", saved.getDestinationCity(),
                        "tripType", saved.getTripType().name(),
                        "totalDays", saved.getTotalDays(),
                        "requestedAdvance",
                        saved.getRequestedAdvance() == null ? "0" : saved.getRequestedAdvance().toPlainString(),
                        "currency", saved.getCurrency())));
        saved.setWorkflowInstanceId(instance.getId());
        saved = trips.save(saved);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "SUBMIT", null, BusinessTripResponse.from(saved));
        return saved;
    }

    /** Final-pass reconciliation after the trip — capture actual expense + paid advance. */
    @Transactional
    public BusinessTripRequest reconcile(UUID tripId, ExpenseReconcileRequest req) {
        BusinessTripRequest t = get(tripId);
        if (t.getStatus() != TripStatus.APPROVED && t.getStatus() != TripStatus.COMPLETED) {
            throw new BadRequestException(
                    "Reconciliation is only allowed on APPROVED or COMPLETED trips");
        }
        BusinessTripResponse before = BusinessTripResponse.from(t);
        t.setActualExpense(req.actualExpense());
        if (req.paidAdvance() != null) t.setPaidAdvance(req.paidAdvance());
        t.setStatus(TripStatus.COMPLETED);
        BusinessTripRequest saved = trips.save(t);
        audit.record(MODULE, ENTITY, tripId.toString(),
                "RECONCILE", before,
                Map.of("actualExpense", req.actualExpense().toPlainString(),
                        "paidAdvance", req.paidAdvance() == null ? "0" : req.paidAdvance().toPlainString(),
                        "reason", req.reason()));
        return saved;
    }

    // ---------- Workflow callbacks ----------

    @Transactional
    public BusinessTripRequest onApproved(UUID tripId, String comment) {
        BusinessTripRequest t = get(tripId);
        if (t.getStatus() != TripStatus.PENDING) return t;
        // On approval, approved_advance defaults to the requested amount unless
        // a manual override happens later in reconciliation.
        if (t.getApprovedAdvance() == null) {
            t.setApprovedAdvance(t.getRequestedAdvance());
        }
        t.setStatus(TripStatus.APPROVED);
        BusinessTripRequest saved = trips.save(t);
        audit.record(MODULE, ENTITY, tripId.toString(), "APPROVED", null,
                Map.of("approvedAdvance",
                        saved.getApprovedAdvance() == null ? "0"
                                : saved.getApprovedAdvance().toPlainString(),
                        "comment", comment == null ? "" : comment));
        // Notify Finance for advance disbursement (PRD §8.6.7 AC).
        eventPublisher.publishEvent(new BusinessTripApprovedEvent(
                saved.getId(),
                saved.getTripNo(),
                saved.getEmployeeId(),
                saved.getDestinationCity(),
                saved.getDestinationCountry(),
                saved.getStartDate(),
                saved.getEndDate(),
                saved.getTotalDays(),
                saved.getApprovedAdvance(),
                saved.getCurrency()));
        return saved;
    }

    @Transactional
    public BusinessTripRequest onRejected(UUID tripId, String comment) {
        BusinessTripRequest t = get(tripId);
        if (t.getStatus() != TripStatus.PENDING) return t;
        t.setStatus(TripStatus.REJECTED);
        BusinessTripRequest saved = trips.save(t);
        audit.record(MODULE, ENTITY, tripId.toString(), "REJECTED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public BusinessTripRequest onCancelled(UUID tripId, String comment) {
        BusinessTripRequest t = get(tripId);
        if (t.getStatus() != TripStatus.PENDING) return t;
        t.setStatus(TripStatus.CANCELLED);
        BusinessTripRequest saved = trips.save(t);
        audit.record(MODULE, ENTITY, tripId.toString(), "CANCELLED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    /** Public helper for the advance balance shown in lists / reports. */
    public BigDecimal computeAdvanceBalance(BusinessTripRequest t) {
        BigDecimal approved = t.getApprovedAdvance() == null ? BigDecimal.ZERO : t.getApprovedAdvance();
        BigDecimal actual = t.getActualExpense() == null ? BigDecimal.ZERO : t.getActualExpense();
        return approved.subtract(actual);
    }
}
