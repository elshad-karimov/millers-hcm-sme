package az.millers.hcm.recruitment.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.KitRequest;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.KitResponse;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.QuestionRequest;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.QuestionResponse;
import az.millers.hcm.recruitment.domain.InterviewKit;
import az.millers.hcm.recruitment.domain.InterviewQuestion;
import az.millers.hcm.recruitment.repo.InterviewKitRepository;
import az.millers.hcm.recruitment.repo.InterviewQuestionRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * CRUD for interview kits + their questions (M85). Code is immutable
 * post-creation so foreign references from {@code interview.kit_id} stay
 * stable. Questions are nested resources — list / add / update / delete
 * via the same service.
 */
@Service
public class InterviewKitService {

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY_KIT = "InterviewKit";
    private static final String ENTITY_Q = "InterviewQuestion";

    private final InterviewKitRepository kits;
    private final InterviewQuestionRepository questions;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public InterviewKitService(InterviewKitRepository kits,
                                InterviewQuestionRepository questions,
                                AuditService audit,
                                CurrentRequest currentRequest) {
        this.kits = kits;
        this.questions = questions;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Kits ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InterviewKit> list(UUID jobFamilyId, boolean activeOnly) {
        if (jobFamilyId != null) {
            return kits.findByJobFamilyIdAndActiveTrueOrderByNameAsc(jobFamilyId);
        }
        return activeOnly ? kits.findByActiveTrueOrderByNameAsc() : kits.findAll();
    }

    @Transactional(readOnly = true)
    public InterviewKit get(UUID id) {
        return kits.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InterviewKit not found: " + id));
    }

    @Transactional
    public InterviewKit create(KitRequest req) {
        String code = normaliseCode(req.code());
        if (kits.findByCode(code).isPresent()) {
            throw new BadRequestException("Kit code already exists: " + code);
        }
        InterviewKit k = new InterviewKit();
        k.setCode(code);
        applyKit(k, req);
        k.setCreatedBy(currentRequest.username());
        k.setUpdatedBy(currentRequest.username());
        InterviewKit saved = kits.save(k);
        audit.record(MODULE, ENTITY_KIT, saved.getId().toString(),
                "CREATE", null, KitResponse.from(saved));
        return saved;
    }

    @Transactional
    public InterviewKit update(UUID id, KitRequest req) {
        InterviewKit k = get(id);
        if (!k.getCode().equalsIgnoreCase(req.code())) {
            throw new BadRequestException("Kit code is immutable");
        }
        KitResponse before = KitResponse.from(k);
        applyKit(k, req);
        k.setUpdatedBy(currentRequest.username());
        InterviewKit saved = kits.save(k);
        audit.record(MODULE, ENTITY_KIT, id.toString(),
                "UPDATE", before, KitResponse.from(saved));
        return saved;
    }

    @Transactional
    public void deactivate(UUID id) {
        InterviewKit k = get(id);
        KitResponse before = KitResponse.from(k);
        k.setActive(false);
        k.setUpdatedBy(currentRequest.username());
        kits.save(k);
        audit.record(MODULE, ENTITY_KIT, id.toString(),
                "DEACTIVATE", before, KitResponse.from(k));
    }

    // ── Questions ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InterviewQuestion> listQuestions(UUID kitId, boolean activeOnly) {
        return activeOnly
                ? questions.findByKitIdAndActiveTrueOrderBySortOrderAscIdAsc(kitId)
                : questions.findByKitIdOrderBySortOrderAscIdAsc(kitId);
    }

    @Transactional
    public InterviewQuestion addQuestion(UUID kitId, QuestionRequest req) {
        if (!kits.existsById(kitId)) {
            throw new BadRequestException("Kit not found: " + kitId);
        }
        InterviewQuestion q = new InterviewQuestion();
        q.setKitId(kitId);
        applyQuestion(q, req);
        InterviewQuestion saved = questions.save(q);
        audit.record(MODULE, ENTITY_Q, saved.getId().toString(),
                "CREATE", null, QuestionResponse.from(saved));
        return saved;
    }

    @Transactional
    public InterviewQuestion updateQuestion(UUID questionId, QuestionRequest req) {
        InterviewQuestion q = questions.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Question not found: " + questionId));
        QuestionResponse before = QuestionResponse.from(q);
        applyQuestion(q, req);
        InterviewQuestion saved = questions.save(q);
        audit.record(MODULE, ENTITY_Q, questionId.toString(),
                "UPDATE", before, QuestionResponse.from(saved));
        return saved;
    }

    @Transactional
    public void deleteQuestion(UUID questionId) {
        InterviewQuestion q = questions.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Question not found: " + questionId));
        QuestionResponse before = QuestionResponse.from(q);
        questions.delete(q);
        audit.record(MODULE, ENTITY_Q, questionId.toString(), "DELETE", before, null);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void applyKit(InterviewKit k, KitRequest req) {
        k.setName(req.name());
        k.setDescription(req.description());
        k.setJobFamilyId(req.jobFamilyId());
        if (req.active() != null) k.setActive(req.active());
    }

    private void applyQuestion(InterviewQuestion q, QuestionRequest req) {
        q.setQuestionText(req.questionText());
        q.setWeight(req.weight() == null ? 1 : req.weight());
        q.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        q.setRequired(req.required() == null ? true : req.required());
        if (req.active() != null) q.setActive(req.active());
    }

    private static String normaliseCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }
}
