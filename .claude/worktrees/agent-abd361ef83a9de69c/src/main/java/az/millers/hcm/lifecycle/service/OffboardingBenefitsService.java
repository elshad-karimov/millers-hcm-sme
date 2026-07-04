package az.millers.hcm.lifecycle.service;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.BenefitsClosureUpdateRequest;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.RehireEligibilityRequest;
import az.millers.hcm.lifecycle.domain.OffboardingBenefitsClosure;
import az.millers.hcm.lifecycle.domain.OffboardingCase;
import az.millers.hcm.lifecycle.event.OffboardingCaseActivatedEvent;
import az.millers.hcm.lifecycle.repo.OffboardingBenefitsClosureRepository;
import az.millers.hcm.lifecycle.repo.OffboardingCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OffboardingBenefitsService {

    private static final List<String> DEFAULT_BENEFIT_TYPES = Arrays.asList(
            "HEALTH_INSURANCE", "LIFE_INSURANCE", "PENSION", "MEAL_ALLOWANCE",
            "TRANSPORT_ALLOWANCE", "MOBILE_PHONE", "LAPTOP_RETURN", "OTHER"
    );

    private final OffboardingBenefitsClosureRepository repo;
    private final OffboardingCaseRepository caseRepo;

    @EventListener
    @Transactional
    public void onCaseActivated(OffboardingCaseActivatedEvent event) {
        try {
            UUID caseId = event.caseId();
            for (String type : DEFAULT_BENEFIT_TYPES) {
                repo.findByCaseIdAndBenefitType(caseId, type).ifPresentOrElse(
                        existing -> {},
                        () -> {
                            OffboardingBenefitsClosure b = new OffboardingBenefitsClosure();
                            b.setCaseId(caseId);
                            b.setBenefitType(type);
                            b.setDescription(type.replace('_', ' ').toLowerCase());
                            repo.save(b);
                        }
                );
            }
        } catch (Exception e) {
            log.warn("Failed to seed benefits closure for case {}: {}", event.caseId(), e.getMessage());
        }
    }

    public List<OffboardingBenefitsClosure> getBenefits(UUID caseId) {
        return repo.findByCaseIdOrderByBenefitType(caseId);
    }

    @Transactional
    public OffboardingBenefitsClosure update(UUID benefitId, BenefitsClosureUpdateRequest req) {
        OffboardingBenefitsClosure b = repo.findById(benefitId)
                .orElseThrow(() -> new IllegalArgumentException("Benefit closure not found: " + benefitId));
        if (req.closureStatus() != null) b.setClosureStatus(req.closureStatus());
        if (req.closedDate() != null) b.setClosedDate(req.closedDate());
        if (req.provider() != null) b.setProvider(req.provider());
        if (req.reference() != null) b.setReference(req.reference());
        if (req.notes() != null) b.setNotes(req.notes());
        OffboardingBenefitsClosure saved = repo.save(b);
        tryAutoCloseBenefits(b.getCaseId());
        return saved;
    }

    @Transactional
    public OffboardingCase updateRehireEligibility(UUID caseId, RehireEligibilityRequest req) {
        OffboardingCase cas = caseRepo.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
        if (req.rehireEligible() != null) cas.setRehireEligible(req.rehireEligible());
        if (req.rehireEligibleFrom() != null) cas.setRehireEligibleFrom(req.rehireEligibleFrom());
        if (req.rehireIneligibleReason() != null) cas.setRehireIneligibleReason(req.rehireIneligibleReason());
        return caseRepo.save(cas);
    }

    private void tryAutoCloseBenefits(UUID caseId) {
        List<OffboardingBenefitsClosure> benefits = repo.findByCaseIdOrderByBenefitType(caseId);
        boolean allClosed = benefits.stream().allMatch(b -> !"PENDING".equals(b.getClosureStatus()));
        if (allClosed) {
            caseRepo.findById(caseId).ifPresent(c -> {
                c.setBenefitsClosureStatus("CLOSED");
                c.setBenefitsClosedAt(OffsetDateTime.now());
                caseRepo.save(c);
            });
        }
    }
}
