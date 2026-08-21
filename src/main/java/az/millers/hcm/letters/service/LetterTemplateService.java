package az.millers.hcm.letters.service;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.common.JsonColumns;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.letters.api.dto.LetterTemplateRequest;
import az.millers.hcm.letters.api.dto.LetterTemplateResponse;
import az.millers.hcm.letters.domain.LetterOutputFormat;
import az.millers.hcm.letters.domain.LetterTemplate;
import az.millers.hcm.letters.repo.LetterTemplateRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Master CRUD for {@link LetterTemplate} (M77 / P2-16). Code is immutable
 * after creation (referenced by FK from {@code letter_request.template_id}).
 */
@Service
public class LetterTemplateService {

    private static final String MODULE = "LETTERS";
    private static final String ENTITY = "LetterTemplate";

    private final LetterTemplateRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final ObjectMapper objectMapper;

    public LetterTemplateService(LetterTemplateRepository repo,
                                  AuditService audit,
                                  CurrentRequest currentRequest,
                                  ObjectMapper objectMapper) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<LetterTemplate> list(boolean activeOnly) {
        return activeOnly
                ? repo.findByActiveTrueOrderByNameAsc()
                : repo.findAll();
    }

    @Transactional(readOnly = true)
    public LetterTemplate get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LetterTemplate not found: " + id));
    }

    @Transactional
    public LetterTemplate create(LetterTemplateRequest req) {
        String code = normaliseCode(req.code());
        String language = req.language() == null ? "en" : req.language();
        // M139 — uniqueness changed from (code) to (code, language); the
        // service rejects duplicates of the same locale variant.
        if (repo.findByCodeAndLanguage(code, language).isPresent()) {
            throw new BadRequestException(
                    "LetterTemplate already exists for code " + code + " language " + language);
        }
        LetterTemplate t = new LetterTemplate();
        t.setCode(code);
        t.setLanguage(language);
        applyRequest(t, req);
        t.setCreatedBy(currentRequest.username());
        t.setUpdatedBy(currentRequest.username());
        LetterTemplate saved = repo.save(t);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, LetterTemplateResponse.from(saved));
        return saved;
    }

    @Transactional
    public LetterTemplate update(UUID id, LetterTemplateRequest req) {
        LetterTemplate t = get(id);
        LetterTemplateResponse before = LetterTemplateResponse.from(t);
        if (!t.getCode().equalsIgnoreCase(req.code())) {
            throw new BadRequestException("LetterTemplate code is immutable");
        }
        applyRequest(t, req);
        t.setUpdatedBy(currentRequest.username());
        LetterTemplate saved = repo.save(t);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, LetterTemplateResponse.from(saved));
        return saved;
    }

    @Transactional
    public void deactivate(UUID id) {
        LetterTemplate t = get(id);
        LetterTemplateResponse before = LetterTemplateResponse.from(t);
        t.setActive(false);
        t.setUpdatedBy(currentRequest.username());
        repo.save(t);
        audit.record(MODULE, ENTITY, id.toString(),
                "DEACTIVATE", before, LetterTemplateResponse.from(t));
    }

    private void applyRequest(LetterTemplate t, LetterTemplateRequest req) {
        t.setName(req.name());
        t.setDescription(req.description());
        t.setBody(req.body());
        t.setPlaceholdersJson(JsonColumns.toNode(objectMapper, req.placeholdersJson()));
        t.setOutputFormat(req.outputFormat() == null
                ? LetterOutputFormat.TEXT : req.outputFormat());
        if (req.requiresApproval() != null) t.setRequiresApproval(req.requiresApproval());
        if (req.active() != null) t.setActive(req.active());
    }

    private static String normaliseCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }
}
