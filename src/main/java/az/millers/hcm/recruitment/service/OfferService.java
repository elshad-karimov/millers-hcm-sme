package az.millers.hcm.recruitment.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import az.millers.hcm.staffing.service.PositionHeadcountService;

@Service
public class OfferService {

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "Offer";

    private final OfferRepository offers;
    private final ApplicationRepository applications;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    // M268 — gate at SEND / ACCEPT against the live position state so
    // a frozen / unfunded / closed position can't accept commitments
    // a week after the vacancy was posted under a different state.
    private final VacancyRepository vacancies;
    private final PositionHeadcountService headcountGate;

    public OfferService(OfferRepository offers,
                         ApplicationRepository applications,
                         AuditService audit,
                         CurrentRequest currentRequest,
                         VacancyRepository vacancies,
                         PositionHeadcountService headcountGate) {
        this.offers = offers;
        this.applications = applications;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.vacancies = vacancies;
        this.headcountGate = headcountGate;
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
        if (o.getStatus() != OfferStatus.DRAFT && o.getStatus() != OfferStatus.SENT) {
            throw new BadRequestException("Cannot edit a " + o.getStatus() + " offer");
        }
        OfferResponse before = o.getId() == null ? null : OfferResponse.from(o);
        o.setProposedSalary(req.proposedSalary());
        o.setCurrency(req.currency() == null ? "AZN" : req.currency().toUpperCase());
        o.setProposedStartDate(req.proposedStartDate());
        o.setBenefits(req.benefits());
        o.setNotes(req.notes());
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
            case DRAFT -> to == OfferStatus.SENT || to == OfferStatus.RESCINDED;
            case SENT -> to == OfferStatus.ACCEPTED || to == OfferStatus.REJECTED
                    || to == OfferStatus.EXPIRED || to == OfferStatus.RESCINDED;
            default -> false;
        };
        if (!ok) throw new BadRequestException(
                "Cannot transition offer from " + from + " to " + to);
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
