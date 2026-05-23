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
import az.millers.hcm.recruitment.repo.ApplicationRepository;
import az.millers.hcm.recruitment.repo.OfferRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class OfferService {

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "Offer";

    private final OfferRepository offers;
    private final ApplicationRepository applications;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public OfferService(OfferRepository offers,
                         ApplicationRepository applications,
                         AuditService audit,
                         CurrentRequest currentRequest) {
        this.offers = offers;
        this.applications = applications;
        this.audit = audit;
        this.currentRequest = currentRequest;
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
}
