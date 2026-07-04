package az.millers.hcm.recruitment.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.OfferRequest;
import az.millers.hcm.recruitment.api.dto.OfferResponse;
import az.millers.hcm.recruitment.domain.Application;
import az.millers.hcm.recruitment.domain.ApplicationStage;
import az.millers.hcm.recruitment.domain.Offer;
import az.millers.hcm.recruitment.domain.OfferStatus;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.repo.ApplicationRepository;
import az.millers.hcm.recruitment.repo.OfferRepository;
import az.millers.hcm.recruitment.repo.VacancyRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.repo.PositionRepository;
import az.millers.hcm.staffing.service.PositionHeadcountService;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;
import az.millers.hcm.workflow.service.WorkflowService;

@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "Offer";

    /** M276 — Recruitment PRD §29-§30: definition codes seeded in V144. */
    public static final String WORKFLOW_STANDARD = "OFFER_APPROVAL";
    public static final String WORKFLOW_EXCEPTION = "OFFER_APPROVAL_EXCEPTION";

    private final OfferRepository offers;
    private final ApplicationRepository applications;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    // M268 — gate at SEND / ACCEPT against the live position state so
    // a frozen / unfunded / closed position can't accept commitments
    // a week after the vacancy was posted under a different state.
    private final VacancyRepository vacancies;
    private final PositionHeadcountService headcountGate;
    // M276 — salary-range validation against the position + approval workflow.
    private final PositionRepository positions;
    private final WorkflowService workflowService;
    // M284 — counteroffer / revision history (PRD §33).
    private final az.millers.hcm.recruitment.repo.OfferRevisionRepository revisions;

    public OfferService(OfferRepository offers,
                         ApplicationRepository applications,
                         AuditService audit,
                         CurrentRequest currentRequest,
                         VacancyRepository vacancies,
                         PositionHeadcountService headcountGate,
                         PositionRepository positions,
                         WorkflowService workflowService,
                         az.millers.hcm.recruitment.repo.OfferRevisionRepository revisions) {
        this.offers = offers;
        this.applications = applications;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.vacancies = vacancies;
        this.headcountGate = headcountGate;
        this.positions = positions;
        this.workflowService = workflowService;
        this.revisions = revisions;
    }

    @Transactional(readOnly = true)
    public Optional<Offer> findForApplication(UUID applicationId) {
        return offers.findByApplicationId(applicationId);
    }

    @Transactional
    public Offer createOrUpdate(UUID applicationId, OfferRequest req) {
        Application app = applications.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application not found: " + applicationId));
        Offer o = offers.findByApplicationId(applicationId).orElseGet(() -> {
            Offer fresh = new Offer();
            fresh.setApplicationId(applicationId);
            fresh.setOfferNo(String.format("OFF-%05d", offers.nextNoSequence()));
            fresh.setStatus(OfferStatus.DRAFT);
            return fresh;
        });
        // M276 — editable in DRAFT; editing an APPROVED offer invalidates
        // the approval (PRD §33: material change after approval triggers
        // re-approval) so it drops back to DRAFT. Frozen while pending.
        if (o.getStatus() == OfferStatus.PENDING_APPROVAL) {
            throw new BadRequestException(
                    "Offer is pending approval — wait for the decision before editing");
        }
        if (o.getStatus() != OfferStatus.DRAFT && o.getStatus() != OfferStatus.APPROVED) {
            throw new BadRequestException("Cannot edit a " + o.getStatus() + " offer");
        }
        OfferResponse before = o.getId() == null ? null : OfferResponse.from(o);
        o.setProposedSalary(req.proposedSalary());
        o.setCurrency(req.currency() == null ? "AZN" : req.currency().toUpperCase());
        o.setProposedStartDate(req.proposedStartDate());
        o.setBenefits(req.benefits());
        o.setNotes(req.notes());
        if (o.getStatus() == OfferStatus.APPROVED) {
            o.setStatus(OfferStatus.DRAFT); // re-approval required
        }
        Offer saved = offers.save(o);
        // Move the application to the OFFER stage if it isn't there yet.
        if (app.getCurrentStage() != ApplicationStage.OFFER
                && app.getCurrentStage() != ApplicationStage.HIRED) {
            app.setCurrentStage(ApplicationStage.OFFER);
            applications.save(app);
        }
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                before == null ? "CREATE" : "UPDATE",
                before, OfferResponse.from(saved));
        return saved;
    }

    @Transactional
    public Offer transition(UUID offerId, OfferStatus newStatus, String notes) {
        Offer o = offers.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found: " + offerId));
        OfferStatus old = o.getStatus();
        validateTransition(old, newStatus);
        // M268 — gate against stale position state. Block SEND + ACCEPT
        // when the position has gone non-fillable since the vacancy was
        // posted. The right gate is assertCanFill: it checks lifecycle
        // (M243), funding (M244), AND headcount room — which is exactly
        // what would block the hire seconds later in EmployeeService.create().
        if (newStatus == OfferStatus.SENT || newStatus == OfferStatus.ACCEPTED) {
            assertPositionStillFillable(o);
        }
        o.setStatus(newStatus);
        if (notes != null && !notes.isBlank()) o.setNotes(notes);
        OffsetDateTime now = OffsetDateTime.now();
        if (newStatus == OfferStatus.SENT) {
            o.setSentAt(now);
            o.setSentBy(currentRequest.username());
        }
        if (newStatus == OfferStatus.ACCEPTED || newStatus == OfferStatus.REJECTED) {
            o.setResponseAt(now);
        }
        Offer saved = offers.save(o);
        audit.record(MODULE, ENTITY, offerId.toString(),
                "TRANSITION", Map.of("status", old.name()),
                Map.of("status", newStatus.name(),
                        "notes", notes == null ? "" : notes));
        return saved;
    }

    private void validateTransition(OfferStatus from, OfferStatus to) {
        boolean ok = switch (from) {
            // M276 — PRD §70: "Offer cannot be sent before approval".
            // DRAFT can only be withdrawn; SENT requires APPROVED.
            case DRAFT -> to == OfferStatus.RESCINDED;
            case APPROVED -> to == OfferStatus.SENT || to == OfferStatus.RESCINDED;
            case SENT -> to == OfferStatus.ACCEPTED || to == OfferStatus.REJECTED
                    || to == OfferStatus.EXPIRED || to == OfferStatus.RESCINDED;
            default -> false;
        };
        if (!ok) throw new BadRequestException(
                from == OfferStatus.DRAFT && to == OfferStatus.SENT
                        ? "Offer must be approved before it can be sent — submit it for approval first"
                        : "Cannot transition offer from " + from + " to " + to);
    }

    // ── M276 — approval state machine (Recruitment PRD §29-§30) ────────

    @Transactional
    public Offer submitForApproval(UUID offerId) {
        Offer o = offers.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found: " + offerId));
        if (o.getStatus() != OfferStatus.DRAFT) {
            throw new BadRequestException(
                    "Only DRAFT offers can be submitted (current: " + o.getStatus() + ")");
        }
        if (o.getProposedSalary() == null) {
            throw new BadRequestException("Offer needs a proposed salary before approval");
        }

        // Salary-range check against the position (fallback: the
        // vacancy's advertised range). Out of range → exception chain.
        SalaryRange range = resolveSalaryRange(o);
        boolean exception = range != null && range.isOutside(o.getProposedSalary());

        String definition = exception ? WORKFLOW_EXCEPTION : WORKFLOW_STANDARD;
        String title = o.getOfferNo() + " — " + o.getProposedSalary() + " " + o.getCurrency()
                + (exception
                        ? " (EXCEPTION: outside range " + range.min() + "–" + range.max() + ")"
                        : "");

        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                definition,
                MODULE,
                ENTITY,
                o.getId().toString(),
                title,
                Map.of(
                        "offerNo", o.getOfferNo(),
                        "proposedSalary", o.getProposedSalary().toPlainString(),
                        "currency", o.getCurrency(),
                        "salaryException", exception,
                        "rangeMin", range == null || range.min() == null ? "" : range.min().toPlainString(),
                        "rangeMax", range == null || range.max() == null ? "" : range.max().toPlainString(),
                        "requestedBy", currentRequest.username())));

        o.setStatus(OfferStatus.PENDING_APPROVAL);
        o.setSalaryException(exception);
        o.setWorkflowInstanceId(instance.getId());
        Offer saved = offers.save(o);
        audit.record(MODULE, ENTITY, offerId.toString(), "SUBMIT_FOR_APPROVAL",
                Map.of("status", OfferStatus.DRAFT.name()),
                Map.of("status", saved.getStatus().name(),
                        "definition", definition,
                        "salaryException", exception,
                        "workflowInstanceId", instance.getId().toString()));
        return saved;
    }

    /** M276 — reacts to the offer approval workflow finishing. */
    @EventListener
    @Transactional
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        if (!WORKFLOW_STANDARD.equals(event.definitionCode())
                && !WORKFLOW_EXCEPTION.equals(event.definitionCode())) {
            return;
        }
        if (!ENTITY.equals(event.subjectEntity())) return;

        UUID offerId;
        try {
            offerId = UUID.fromString(event.subjectId());
        } catch (IllegalArgumentException e) {
            log.warn("Offer approval: invalid subjectId '{}'", event.subjectId());
            return;
        }
        Offer o = offers.findById(offerId).orElse(null);
        if (o == null) {
            log.warn("Offer approval: offer {} not found for workflow {}",
                    offerId, event.instanceId());
            return;
        }
        if (o.getStatus() != OfferStatus.PENDING_APPROVAL) return; // idempotent guard

        OfferStatus target = switch (event.status()) {
            case APPROVED, AUTO_APPROVED -> OfferStatus.APPROVED;
            // REJECTED / RETURNED / CANCELLED all land back in DRAFT so
            // the recruiter can revise the salary and resubmit — an
            // offer has no terminal "approval rejected" state because
            // the negotiation continues.
            default -> OfferStatus.DRAFT;
        };
        o.setStatus(target);
        offers.save(o);
        audit.record(MODULE, ENTITY, offerId.toString(), "APPROVAL_OUTCOME",
                Map.of("status", OfferStatus.PENDING_APPROVAL.name()),
                Map.of("status", target.name(),
                        "workflowStatus", event.status().name(),
                        "actor", event.actor() == null ? "" : event.actor(),
                        "comment", event.comment() == null ? "" : event.comment()));
        log.info("Offer {} approval outcome: {} → {} (by {})",
                o.getOfferNo(), event.status(), target, event.actor());
    }

    // ── M284 — counteroffer + revision (Recruitment PRD §33) ───────────

    public record ReviseRequest(
            java.math.BigDecimal proposedSalary,
            String currency,
            java.time.LocalDate proposedStartDate,
            String benefits,
            az.millers.hcm.recruitment.domain.OfferRevision.Reason reason,
            String notes) {}

    /**
     * Apply revised terms to an APPROVED or SENT offer. The previous
     * terms are snapshotted into offer_revision, then PRD §33's rule
     * fires: "any material change after approval should trigger
     * re-approval" — the offer drops to DRAFT and goes back through
     * the M276 approval workflow (salary-exception routing included,
     * so a counter ABOVE the range escalates to the longer chain).
     */
    @Transactional
    public Offer revise(UUID offerId, ReviseRequest req) {
        Offer o = offers.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found: " + offerId));
        if (o.getStatus() != OfferStatus.APPROVED && o.getStatus() != OfferStatus.SENT) {
            throw new BadRequestException(
                    "Only APPROVED or SENT offers can be revised (current: " + o.getStatus()
                    + "). DRAFT offers are edited directly.");
        }
        if (req.reason() == null) {
            throw new BadRequestException("Revision reason is required");
        }
        if (req.proposedSalary() == null) {
            throw new BadRequestException("Revised salary is required");
        }

        // Snapshot the terms BEFORE applying the change.
        var rev = new az.millers.hcm.recruitment.domain.OfferRevision();
        rev.setOfferId(o.getId());
        rev.setRevisionNo(revisions.countByOfferId(o.getId()) + 1);
        rev.setPrevSalary(o.getProposedSalary());
        rev.setPrevCurrency(o.getCurrency());
        rev.setPrevStartDate(o.getProposedStartDate());
        rev.setPrevBenefits(o.getBenefits());
        rev.setPrevStatus(o.getStatus().name());
        rev.setReason(req.reason());
        rev.setNotes(req.notes());
        rev.setCreatedBy(currentRequest.username());
        revisions.save(rev);

        OfferStatus old = o.getStatus();
        o.setProposedSalary(req.proposedSalary());
        if (req.currency() != null && !req.currency().isBlank()) {
            o.setCurrency(req.currency().toUpperCase());
        }
        if (req.proposedStartDate() != null) o.setProposedStartDate(req.proposedStartDate());
        if (req.benefits() != null) o.setBenefits(req.benefits());
        o.setStatus(OfferStatus.DRAFT); // §33 — re-approval required
        o.setSalaryException(false);    // re-evaluated on next submit
        Offer saved = offers.save(o);

        audit.record(MODULE, ENTITY, offerId.toString(), "REVISE",
                Map.of("status", old.name(),
                        "salary", rev.getPrevSalary() == null ? "" : rev.getPrevSalary().toPlainString()),
                Map.of("status", saved.getStatus().name(),
                        "salary", saved.getProposedSalary().toPlainString(),
                        "revisionNo", rev.getRevisionNo(),
                        "reason", req.reason().name()));
        return saved;
    }

    @Transactional(readOnly = true)
    public java.util.List<az.millers.hcm.recruitment.domain.OfferRevision> revisions(UUID offerId) {
        return revisions.findByOfferIdOrderByRevisionNoDesc(offerId);
    }

    /** Position range first (source of truth), vacancy range as fallback. */
    private SalaryRange resolveSalaryRange(Offer o) {
        Application app = applications.findById(o.getApplicationId()).orElse(null);
        if (app == null) return null;
        Vacancy v = vacancies.findById(app.getVacancyId()).orElse(null);
        if (v == null) return null;
        if (v.getPositionId() != null) {
            Position p = positions.findById(v.getPositionId()).orElse(null);
            if (p != null && (p.getSalaryMin() != null || p.getSalaryMax() != null)) {
                return new SalaryRange(p.getSalaryMin(), p.getSalaryMax());
            }
        }
        if (v.getSalaryMin() != null || v.getSalaryMax() != null) {
            return new SalaryRange(v.getSalaryMin(), v.getSalaryMax());
        }
        return null;
    }

    private record SalaryRange(BigDecimal min, BigDecimal max) {
        boolean isOutside(BigDecimal salary) {
            if (min != null && salary.compareTo(min) < 0) return true;
            if (max != null && salary.compareTo(max) > 0) return true;
            return false;
        }
    }

    /**
     * M268 — refuse to SEND or ACCEPT an offer when the position can no
     * longer be filled. Vacancy → Application → Offer chain may take
     * weeks; the position state can have changed since posting (frozen
     * during a reorg, funding expired, headcount filled by a parallel
     * direct hire, etc.). The same gate that {@code EmployeeService.create}
     * uses to block hires is the right gate here too — applied EARLIER
     * so the candidate never gets an offer that would fail at hire.
     *
     * <p>No-op when the application's vacancy has no linked position
     * (legacy data) — we don't want to block historical offers that
     * predate the position-control gate.
     */
    private void assertPositionStillFillable(Offer o) {
        Application app = applications.findById(o.getApplicationId()).orElse(null);
        if (app == null) return;
        Vacancy v = vacancies.findById(app.getVacancyId()).orElse(null);
        if (v == null || v.getPositionId() == null) return;
        headcountGate.assertCanFill(v.getPositionId());
    }
}
