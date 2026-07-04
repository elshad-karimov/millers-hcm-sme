package az.millers.hcm.performance.service;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.performance.api.dto.ReviewCycleRequest;
import az.millers.hcm.performance.api.dto.ReviewCycleResponse;
import az.millers.hcm.performance.domain.CycleStatus;
import az.millers.hcm.performance.domain.ReviewCycle;
import az.millers.hcm.performance.event.ReviewCycleCompletedEvent;
import az.millers.hcm.performance.repo.ReviewCycleRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class ReviewCycleService {

    private static final String MODULE = "PERFORMANCE";
    private static final String ENTITY = "ReviewCycle";

    private final ReviewCycleRepository cycles;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final ApplicationEventPublisher eventPublisher;

    public ReviewCycleService(ReviewCycleRepository cycles,
                              AuditService audit,
                              CurrentRequest currentRequest,
                              ApplicationEventPublisher eventPublisher) {
        this.cycles = cycles;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public ReviewCycle get(UUID id) {
        return cycles.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review cycle not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ReviewCycle> list(CycleStatus status) {
        return status == null
                ? cycles.findAllByOrderByPeriodStartDesc()
                : cycles.findByStatusOrderByPeriodStartDesc(status);
    }

    @Transactional
    public ReviewCycle create(ReviewCycleRequest req) {
        if (req.periodEnd().isBefore(req.periodStart())) {
            throw new BadRequestException("periodEnd must be on or after periodStart");
        }
        if (cycles.findByCode(req.code()).isPresent()) {
            throw new BadRequestException("A review cycle with this code already exists: " + req.code());
        }
        ReviewCycle c = new ReviewCycle();
        apply(c, req);
        c.setStatus(CycleStatus.DRAFT);
        c.setCreatedBy(currentRequest.username());
        c.setUpdatedBy(currentRequest.username());
        ReviewCycle saved = cycles.save(c);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, ReviewCycleResponse.from(saved));
        return saved;
    }

    @Transactional
    public ReviewCycle update(UUID id, ReviewCycleRequest req) {
        ReviewCycle c = get(id);
        if (c.getStatus() == CycleStatus.COMPLETED) {
            throw new BadRequestException("Cannot edit a COMPLETED cycle");
        }
        ReviewCycleResponse before = ReviewCycleResponse.from(c);
        apply(c, req);
        c.setUpdatedBy(currentRequest.username());
        ReviewCycle saved = cycles.save(c);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, ReviewCycleResponse.from(saved));
        return saved;
    }

    @Transactional
    public ReviewCycle changeStatus(UUID id, CycleStatus next, String reason) {
        ReviewCycle c = get(id);
        if (c.getStatus() == next) {
            throw new BadRequestException("Cycle is already in status " + next);
        }
        CycleStatus old = c.getStatus();
        c.setStatus(next);
        c.setUpdatedBy(currentRequest.username());
        ReviewCycle saved = cycles.save(c);
        audit.record(MODULE, ENTITY, id.toString(),
                "STATUS_CHANGE",
                java.util.Map.of("from", old.name()),
                java.util.Map.of("to", next.name(), "reason", reason == null ? "" : reason));

        // When a cycle completes, publish an event so the bonus engine can
        // auto-generate a BonusRun (PRD §8.13 AC / M184).
        if (next == CycleStatus.COMPLETED) {
            eventPublisher.publishEvent(new ReviewCycleCompletedEvent(
                    saved.getId(),
                    saved.getCode(),
                    saved.getName(),
                    saved.getCycleType(),
                    saved.getPeriodEnd()));
        }

        return saved;
    }

    private void apply(ReviewCycle c, ReviewCycleRequest req) {
        c.setCode(req.code());
        c.setName(req.name());
        c.setCycleType(req.cycleType());
        c.setPeriodStart(req.periodStart());
        c.setPeriodEnd(req.periodEnd());
        c.setSelfReviewDue(req.selfReviewDue());
        c.setManagerReviewDue(req.managerReviewDue());
        c.setFinalDue(req.finalDue());
        c.setDescription(req.description());
        if (req.ratingScale() != null) c.setRatingScale(req.ratingScale());
    }
}
