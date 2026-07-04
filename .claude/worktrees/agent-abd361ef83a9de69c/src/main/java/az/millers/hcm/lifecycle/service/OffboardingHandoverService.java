package az.millers.hcm.lifecycle.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.HandoverTaskCreateRequest;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.HandoverTaskUpdateRequest;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.ReplacementUpdateRequest;
import az.millers.hcm.lifecycle.domain.HandoverStatus;
import az.millers.hcm.lifecycle.domain.OffboardingCase;
import az.millers.hcm.lifecycle.domain.OffboardingHandoverTask;
import az.millers.hcm.lifecycle.repo.OffboardingCaseRepository;
import az.millers.hcm.lifecycle.repo.OffboardingHandoverTaskRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OffboardingHandoverService {

    private final OffboardingHandoverTaskRepository taskRepo;
    private final OffboardingCaseRepository caseRepo;

    public List<OffboardingHandoverTask> getTasks(UUID caseId) {
        return taskRepo.findByCaseIdOrderByCreatedAt(caseId);
    }

    @Transactional
    public OffboardingHandoverTask createTask(UUID caseId, HandoverTaskCreateRequest req) {
        OffboardingHandoverTask task = new OffboardingHandoverTask();
        task.setCaseId(caseId);
        task.setTitle(req.title());
        task.setCategory(req.category() != null ? req.category() : "GENERAL");
        task.setDescription(req.description());
        task.setHandoverTo(req.handoverTo());
        task.setDueDate(req.dueDate());
        task.setNotes(req.notes());
        task.setHandoverStatus(HandoverStatus.PENDING);
        return taskRepo.save(task);
    }

    @Transactional
    public OffboardingHandoverTask updateTask(UUID taskId, HandoverTaskUpdateRequest req) {
        OffboardingHandoverTask task = taskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Handover task not found: " + taskId));
        if (req.handoverStatus() != null) {
            task.setHandoverStatus(req.handoverStatus());
            if (req.handoverStatus() == HandoverStatus.TRANSFERRED && task.getCompletedAt() == null) {
                task.setCompletedAt(OffsetDateTime.now());
            }
        }
        if (req.handoverTo() != null) task.setHandoverTo(req.handoverTo());
        if (req.dueDate() != null) task.setDueDate(req.dueDate());
        if (req.notes() != null) task.setNotes(req.notes());
        return taskRepo.save(task);
    }

    @Transactional
    public OffboardingCase updateReplacement(UUID caseId, ReplacementUpdateRequest req) {
        OffboardingCase cas = caseRepo.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Offboarding case not found: " + caseId));
        if (req.replacementRequired() != null) cas.setReplacementRequired(req.replacementRequired());
        if (req.replacementNotes() != null) cas.setReplacementNotes(req.replacementNotes());
        if (Boolean.TRUE.equals(req.replacementRequired()) && cas.getVacancyTriggeredAt() == null) {
            cas.setVacancyTriggeredAt(OffsetDateTime.now());
        }
        return caseRepo.save(cas);
    }
}
