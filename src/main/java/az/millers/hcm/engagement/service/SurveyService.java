package az.millers.hcm.engagement.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.engagement.api.dto.SurveyDtos.AnswerRequest;
import az.millers.hcm.engagement.api.dto.SurveyDtos.AnswerResponse;
import az.millers.hcm.engagement.api.dto.SurveyDtos.CampaignRequest;
import az.millers.hcm.engagement.api.dto.SurveyDtos.CampaignResponse;
import az.millers.hcm.engagement.api.dto.SurveyDtos.CampaignResults;
import az.millers.hcm.engagement.api.dto.SurveyDtos.QuestionAggregate;
import az.millers.hcm.engagement.api.dto.SurveyDtos.QuestionRequest;
import az.millers.hcm.engagement.api.dto.SurveyDtos.QuestionResponse;
import az.millers.hcm.engagement.api.dto.SurveyDtos.ResponseResponse;
import az.millers.hcm.engagement.api.dto.SurveyDtos.SubmitRequest;
import az.millers.hcm.engagement.api.dto.SurveyDtos.TemplateRequest;
import az.millers.hcm.engagement.api.dto.SurveyDtos.TemplateResponse;
import az.millers.hcm.engagement.domain.CampaignStatus;
import az.millers.hcm.engagement.domain.QuestionType;
import az.millers.hcm.engagement.domain.SurveyAnswer;
import az.millers.hcm.engagement.domain.SurveyCampaign;
import az.millers.hcm.engagement.domain.SurveyQuestion;
import az.millers.hcm.engagement.domain.SurveyResponse;
import az.millers.hcm.engagement.domain.SurveyTemplate;
import az.millers.hcm.engagement.repo.SurveyAnswerRepository;
import az.millers.hcm.engagement.repo.SurveyCampaignRepository;
import az.millers.hcm.engagement.repo.SurveyQuestionRepository;
import az.millers.hcm.engagement.repo.SurveyResponseRepository;
import az.millers.hcm.engagement.repo.SurveyTemplateRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Engagement surveys + eNPS orchestration (M116). Templates → campaigns →
 * responses → answers → aggregate results.
 *
 * <p>Pure math lives in {@link SurveyAggregator}. This class wires the
 * persistence, validates inputs (window math, type-payload pairings),
 * and audits every write.
 */
@Service
public class SurveyService {

    private static final String MODULE = "ENGAGEMENT";
    private static final String TEMPLATE_ENTITY = "SurveyTemplate";
    private static final String CAMPAIGN_ENTITY = "SurveyCampaign";
    private static final String RESPONSE_ENTITY = "SurveyResponse";

    private final SurveyTemplateRepository templates;
    private final SurveyQuestionRepository questions;
    private final SurveyCampaignRepository campaigns;
    private final SurveyResponseRepository responses;
    private final SurveyAnswerRepository answers;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public SurveyService(SurveyTemplateRepository templates,
                          SurveyQuestionRepository questions,
                          SurveyCampaignRepository campaigns,
                          SurveyResponseRepository responses,
                          SurveyAnswerRepository answers,
                          AuditService audit,
                          CurrentRequest currentRequest) {
        this.templates = templates;
        this.questions = questions;
        this.campaigns = campaigns;
        this.responses = responses;
        this.answers = answers;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ─── Templates ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TemplateResponse> listTemplates(boolean activeOnly) {
        List<SurveyTemplate> rows = activeOnly
                ? templates.findByActiveTrueOrderByNameAsc()
                : templates.findAllByOrderByNameAsc();
        return rows.stream().map(this::decorate).toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse getTemplate(UUID id) {
        return decorate(loadTemplate(id));
    }

    @Transactional
    public TemplateResponse createTemplate(TemplateRequest req) {
        validateTemplate(req);
        if (templates.existsByCode(req.code())) {
            throw new BadRequestException("Template code already exists: " + req.code());
        }
        SurveyTemplate t = new SurveyTemplate();
        applyTemplate(t, req);
        t.setCreatedBy(currentRequest.username());
        SurveyTemplate saved = templates.save(t);
        replaceQuestions(saved.getId(), req.questions());
        TemplateResponse response = decorate(saved);
        audit.record(MODULE, TEMPLATE_ENTITY, saved.getId().toString(),
                "CREATE", null, response);
        return response;
    }

    @Transactional
    public TemplateResponse updateTemplate(UUID id, TemplateRequest req) {
        validateTemplate(req);
        SurveyTemplate t = loadTemplate(id);
        if (!t.getCode().equals(req.code()) && templates.existsByCode(req.code())) {
            throw new BadRequestException("Template code already exists: " + req.code());
        }
        TemplateResponse before = decorate(t);
        applyTemplate(t, req);
        templates.save(t);
        replaceQuestions(id, req.questions());
        TemplateResponse response = decorate(t);
        audit.record(MODULE, TEMPLATE_ENTITY, id.toString(), "UPDATE", before, response);
        return response;
    }

    private void applyTemplate(SurveyTemplate t, TemplateRequest req) {
        t.setCode(req.code());
        t.setName(req.name());
        t.setDescription(req.description());
        t.setAnonymous(req.anonymous() != null && req.anonymous());
        t.setActive(req.active() == null ? true : req.active());
    }

    private void replaceQuestions(UUID templateId, List<QuestionRequest> reqs) {
        questions.deleteAllByTemplateId(templateId);
        for (QuestionRequest r : reqs) {
            SurveyQuestion q = new SurveyQuestion();
            q.setTemplateId(templateId);
            q.setOrderIndex(r.orderIndex());
            q.setPrompt(r.prompt());
            q.setQuestionType(r.questionType());
            q.setMetadata(r.metadata());
            q.setRequired(r.required() == null ? true : r.required());
            questions.save(q);
        }
    }

    private SurveyTemplate loadTemplate(UUID id) {
        return templates.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Survey template not found: " + id));
    }

    private TemplateResponse decorate(SurveyTemplate t) {
        List<QuestionResponse> qs = questions
                .findByTemplateIdOrderByOrderIndexAsc(t.getId())
                .stream().map(QuestionResponse::from).toList();
        return TemplateResponse.from(t, qs);
    }

    /** Package-private — pinned by the unit test. */
    static void validateTemplate(TemplateRequest req) {
        if (req.questions() == null || req.questions().isEmpty()) {
            throw new BadRequestException("Template requires at least one question");
        }
        Set<Integer> seen = new HashSet<>();
        for (QuestionRequest q : req.questions()) {
            if (q.orderIndex() < 0) {
                throw new BadRequestException("orderIndex must be >= 0");
            }
            if (!seen.add(q.orderIndex())) {
                throw new BadRequestException("Duplicate orderIndex: " + q.orderIndex());
            }
            if (q.prompt() == null || q.prompt().isBlank()) {
                throw new BadRequestException("Question prompt is required");
            }
            if (q.questionType() == null) {
                throw new BadRequestException("Question type is required");
            }
        }
    }

    // ─── Campaigns ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CampaignResponse> listCampaigns(CampaignStatus status) {
        List<SurveyCampaign> rows = status == null
                ? campaigns.findAllByOrderByCreatedAtDesc()
                : campaigns.findByStatusOrderByCreatedAtDesc(status);
        return rows.stream().map(this::decorateCampaign).toList();
    }

    @Transactional(readOnly = true)
    public CampaignResponse getCampaign(UUID id) {
        return decorateCampaign(loadCampaign(id));
    }

    /** Active campaigns open right now — used by the employee self-service list. */
    @Transactional(readOnly = true)
    public List<CampaignResponse> openCampaignsToday() {
        return campaigns.findOpenOn(LocalDate.now()).stream()
                .map(this::decorateCampaign).toList();
    }

    @Transactional
    public CampaignResponse createCampaign(CampaignRequest req) {
        SurveyTemplate t = loadTemplate(req.templateId());
        if (!t.isActive()) {
            throw new BadRequestException(
                    "Template " + t.getCode() + " is inactive — cannot launch a campaign");
        }
        if (questions.countByTemplateId(t.getId()) == 0) {
            throw new BadRequestException(
                    "Template " + t.getCode() + " has no questions");
        }
        if (req.closesOn().isBefore(req.opensOn())) {
            throw new BadRequestException("closesOn must be on or after opensOn");
        }
        SurveyCampaign c = new SurveyCampaign();
        c.setTemplateId(t.getId());
        c.setName(req.name());
        c.setDescription(req.description());
        c.setOpensOn(req.opensOn());
        c.setClosesOn(req.closesOn());
        c.setTargetAll(req.targetAll() == null ? true : req.targetAll());
        c.setStatus(CampaignStatus.DRAFT);
        c.setCreatedBy(currentRequest.username());
        SurveyCampaign saved = campaigns.save(c);
        CampaignResponse response = decorateCampaign(saved);
        audit.record(MODULE, CAMPAIGN_ENTITY, saved.getId().toString(),
                "CREATE", null, response);
        return response;
    }

    @Transactional
    public CampaignResponse changeStatus(UUID id, CampaignStatus newStatus) {
        SurveyCampaign c = loadCampaign(id);
        if (c.getStatus() == newStatus) {
            throw new BadRequestException("Campaign is already " + newStatus);
        }
        validateTransition(c.getStatus(), newStatus);
        CampaignResponse before = decorateCampaign(c);
        c.setStatus(newStatus);
        SurveyCampaign saved = campaigns.save(c);
        CampaignResponse response = decorateCampaign(saved);
        audit.record(MODULE, CAMPAIGN_ENTITY, id.toString(),
                "STATUS_CHANGE", before, response);
        return response;
    }

    /** Package-private — pinned by the unit test. */
    static void validateTransition(CampaignStatus from, CampaignStatus to) {
        if (from == CampaignStatus.CLOSED || from == CampaignStatus.CANCELLED) {
            throw new BadRequestException(
                    "Campaign in " + from + " state cannot transition further");
        }
        // Allowed: DRAFT → ACTIVE / CANCELLED, ACTIVE → CLOSED / CANCELLED.
        if (from == CampaignStatus.DRAFT
                && to != CampaignStatus.ACTIVE
                && to != CampaignStatus.CANCELLED) {
            throw new BadRequestException(
                    "DRAFT can only go to ACTIVE or CANCELLED (got " + to + ")");
        }
        if (from == CampaignStatus.ACTIVE
                && to != CampaignStatus.CLOSED
                && to != CampaignStatus.CANCELLED) {
            throw new BadRequestException(
                    "ACTIVE can only go to CLOSED or CANCELLED (got " + to + ")");
        }
    }

    private SurveyCampaign loadCampaign(UUID id) {
        return campaigns.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Survey campaign not found: " + id));
    }

    private CampaignResponse decorateCampaign(SurveyCampaign c) {
        SurveyTemplate t = templates.findById(c.getTemplateId()).orElse(null);
        long count = responses.countByCampaignId(c.getId());
        return CampaignResponse.from(c, t, count);
    }

    // ─── Submission ─────────────────────────────────────────────────────

    @Transactional
    public ResponseResponse submit(SubmitRequest req, UUID employeeId) {
        SurveyCampaign c = loadCampaign(req.campaignId());
        if (c.getStatus() != CampaignStatus.ACTIVE) {
            throw new BadRequestException("Campaign is not ACTIVE");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(c.getOpensOn()) || today.isAfter(c.getClosesOn())) {
            throw new BadRequestException("Campaign is outside its date window");
        }
        SurveyTemplate t = loadTemplate(c.getTemplateId());
        List<SurveyQuestion> tplQuestions =
                questions.findByTemplateIdOrderByOrderIndexAsc(t.getId());
        if (tplQuestions.isEmpty()) {
            throw new BadRequestException("Template has no questions");
        }
        // Validate every required question got an answer.
        Map<UUID, SurveyQuestion> qById = new HashMap<>();
        for (SurveyQuestion q : tplQuestions) qById.put(q.getId(), q);
        Set<UUID> answered = new HashSet<>();
        for (AnswerRequest a : req.answers()) {
            SurveyQuestion q = qById.get(a.questionId());
            if (q == null) {
                throw new BadRequestException("Unknown question: " + a.questionId());
            }
            validateAnswer(q, a);
            answered.add(a.questionId());
        }
        for (SurveyQuestion q : tplQuestions) {
            if (q.isRequired() && !answered.contains(q.getId())) {
                throw new BadRequestException(
                        "Missing required answer for: " + q.getPrompt());
            }
        }

        // Anonymous → employee_id is null. Identified → enforce one-per-(campaign, employee).
        UUID effectiveEmployeeId = t.isAnonymous() ? null : employeeId;
        if (effectiveEmployeeId != null) {
            Optional<SurveyResponse> existing =
                    responses.findByCampaignIdAndEmployeeId(c.getId(), effectiveEmployeeId);
            if (existing.isPresent()) {
                throw new BadRequestException(
                        "You have already submitted a response to this campaign");
            }
        }

        SurveyResponse r = new SurveyResponse();
        r.setCampaignId(c.getId());
        r.setEmployeeId(effectiveEmployeeId);
        r.setComment(req.comment());
        SurveyResponse savedResponse = responses.save(r);

        List<AnswerResponse> savedAnswers = new ArrayList<>(req.answers().size());
        for (AnswerRequest a : req.answers()) {
            SurveyAnswer ans = new SurveyAnswer();
            ans.setResponseId(savedResponse.getId());
            ans.setQuestionId(a.questionId());
            ans.setRatingValue(a.ratingValue());
            ans.setTextValue(a.textValue());
            ans.setChoiceValue(a.choiceValue());
            SurveyAnswer savedAns = answers.save(ans);
            savedAnswers.add(AnswerResponse.from(savedAns));
        }

        audit.record(MODULE, RESPONSE_ENTITY, savedResponse.getId().toString(),
                "SUBMIT", null,
                Map.of(
                        "campaignId", c.getId().toString(),
                        "anonymous", t.isAnonymous(),
                        "answerCount", savedAnswers.size()));
        return ResponseResponse.from(savedResponse, savedAnswers);
    }

    /** Package-private — pinned by the unit test. */
    static void validateAnswer(SurveyQuestion q, AnswerRequest a) {
        QuestionType type = q.getQuestionType();
        switch (type) {
            case RATING_1_5 -> {
                if (a.ratingValue() == null || a.ratingValue() < 1 || a.ratingValue() > 5) {
                    throw new BadRequestException(
                            "RATING_1_5 expects an integer in [1, 5] for question: "
                                    + q.getPrompt());
                }
            }
            case RATING_1_10 -> {
                if (a.ratingValue() == null || a.ratingValue() < 0 || a.ratingValue() > 10) {
                    throw new BadRequestException(
                            "RATING_1_10 expects an integer in [0, 10] for question: "
                                    + q.getPrompt());
                }
            }
            case BOOLEAN -> {
                if (a.ratingValue() == null || (a.ratingValue() != 0 && a.ratingValue() != 1)) {
                    throw new BadRequestException(
                            "BOOLEAN expects 0 or 1 for question: " + q.getPrompt());
                }
            }
            case TEXT -> {
                if (a.textValue() == null || a.textValue().isBlank()) {
                    if (q.isRequired()) {
                        throw new BadRequestException(
                                "TEXT requires a non-empty answer for: " + q.getPrompt());
                    }
                }
            }
            case MULTIPLE_CHOICE -> {
                if (a.choiceValue() == null || a.choiceValue().isBlank()) {
                    throw new BadRequestException(
                            "MULTIPLE_CHOICE requires a choice for: " + q.getPrompt());
                }
            }
        }
    }

    // ─── Results ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CampaignResults results(UUID campaignId) {
        SurveyCampaign c = loadCampaign(campaignId);
        List<SurveyQuestion> tplQuestions =
                questions.findByTemplateIdOrderByOrderIndexAsc(c.getTemplateId());
        List<SurveyAnswer> allAnswers = answers.findByCampaignId(campaignId);
        Map<UUID, List<SurveyAnswer>> byQuestion = new HashMap<>();
        for (SurveyAnswer a : allAnswers) {
            byQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
        }
        List<QuestionAggregate> aggregates = new ArrayList<>(tplQuestions.size());
        for (SurveyQuestion q : tplQuestions) {
            aggregates.add(SurveyAggregator.aggregate(
                    q,
                    byQuestion.getOrDefault(q.getId(), List.of()),
                    SurveyAggregator.TEXT_SAMPLE_LIMIT));
        }
        return new CampaignResults(
                c.getId(), c.getName(),
                (int) responses.countByCampaignId(campaignId),
                aggregates);
    }

    @Transactional(readOnly = true)
    public List<ResponseResponse> responsesFor(UUID campaignId) {
        List<SurveyResponse> all = responses
                .findByCampaignIdOrderBySubmittedAtDesc(campaignId);
        if (all.isEmpty()) return List.of();
        Set<UUID> respIds = new HashSet<>();
        for (SurveyResponse r : all) respIds.add(r.getId());
        Map<UUID, List<AnswerResponse>> byResp = new HashMap<>();
        for (UUID rid : respIds) {
            byResp.put(rid, answers.findByResponseId(rid).stream()
                    .map(AnswerResponse::from).toList());
        }
        return all.stream()
                .map(r -> ResponseResponse.from(r,
                        byResp.getOrDefault(r.getId(), List.of())))
                .toList();
    }
}
