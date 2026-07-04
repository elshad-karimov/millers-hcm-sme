package az.millers.hcm.lifecycle.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.lifecycle.api.dto.ExitInterviewRequest;
import az.millers.hcm.lifecycle.domain.ExitInterview;
import az.millers.hcm.lifecycle.domain.OffboardingCase;
import az.millers.hcm.lifecycle.repo.ExitInterviewRepository;
import az.millers.hcm.lifecycle.repo.OffboardingCaseRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OffboardingExitInterviewService {

    private final ExitInterviewRepository repo;
    private final OffboardingCaseRepository caseRepo;

    public Optional<ExitInterview> findByCaseId(UUID caseId) {
        return repo.findByCaseId(caseId);
    }

    @Transactional
    public ExitInterview save(UUID caseId, ExitInterviewRequest req) {
        OffboardingCase cas = caseRepo.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Offboarding case not found: " + caseId));
        ExitInterview ei = repo.findByCaseId(caseId).orElseGet(ExitInterview::new);
        ei.setCaseId(caseId);
        if (cas.getTerminationId() != null && ei.getTerminationId() == null) {
            ei.setTerminationId(cas.getTerminationId());
        }
        if (req.conductedBy() != null) ei.setConductedBy(req.conductedBy());
        if (req.overallRating() != null) ei.setOverallRating(req.overallRating());
        if (req.wouldRecommend() != null) ei.setWouldRecommend(req.wouldRecommend());
        if (req.reasonForLeaving() != null) ei.setReasonForLeaving(req.reasonForLeaving());
        if (req.feedback() != null) ei.setFeedback(req.feedback());
        if (req.improvementSuggestions() != null) ei.setImprovementSuggestions(req.improvementSuggestions());
        if (req.interviewMode() != null) ei.setInterviewMode(req.interviewMode());
        if (req.interviewDate() != null) ei.setInterviewDate(req.interviewDate());
        if (req.followUpRequired() != null) ei.setFollowUpRequired(req.followUpRequired());
        if (req.followUpNotes() != null) ei.setFollowUpNotes(req.followUpNotes());
        if (ei.getConductedAt() == null) ei.setConductedAt(OffsetDateTime.now());
        return repo.save(ei);
    }
}
