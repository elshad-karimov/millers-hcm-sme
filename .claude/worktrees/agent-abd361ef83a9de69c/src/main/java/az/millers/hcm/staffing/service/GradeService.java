package az.millers.hcm.staffing.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.api.dto.GradeRequest;
import az.millers.hcm.staffing.api.dto.GradeResponse;
import az.millers.hcm.staffing.domain.Grade;
import az.millers.hcm.staffing.repo.GradeRepository;

/**
 * Master CRUD for {@link Grade} — pay bands referenced by {@code Position.gradeId}
 * (M75 / P2-19). Uppercase + dash code, ISO 4217 currency, salary range
 * sanity-checked at the service layer (DB also enforces min ≤ max).
 */
@Service
public class GradeService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "Grade";

    private final GradeRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public GradeService(GradeRepository repo, AuditService audit, CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<Grade> list(boolean activeOnly) {
        return activeOnly
                ? repo.findByActiveTrueOrderByLevelAscNameAsc()
                : repo.findAll();
    }

    @Transactional(readOnly = true)
    public Grade get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grade not found: " + id));
    }

    @Transactional
    public Grade create(GradeRequest req) {
        String code = normaliseCode(req.code());
        if (repo.findByCode(code).isPresent()) {
            throw new BadRequestException("Grade code already exists: " + code);
        }
        Grade g = new Grade();
        g.setCode(code);
        applyRequest(g, req);
        g.setCreatedBy(currentRequest.username());
        g.setUpdatedBy(currentRequest.username());
        Grade saved = repo.save(g);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, GradeResponse.from(saved));
        return saved;
    }

    @Transactional
    public Grade update(UUID id, GradeRequest req) {
        Grade g = get(id);
        GradeResponse before = GradeResponse.from(g);
        // Code is immutable after creation (referenced by FK on position.grade_id).
        if (!g.getCode().equalsIgnoreCase(req.code())) {
            throw new BadRequestException("Grade code is immutable; create a new grade instead");
        }
        applyRequest(g, req);
        g.setUpdatedBy(currentRequest.username());
        Grade saved = repo.save(g);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, GradeResponse.from(saved));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        Grade g = get(id);
        GradeResponse before = GradeResponse.from(g);
        // Soft-delete: flip active flag. Hard-delete would break Position.grade_id FK.
        g.setActive(false);
        g.setUpdatedBy(currentRequest.username());
        repo.save(g);
        audit.record(MODULE, ENTITY, id.toString(),
                "DEACTIVATE", before, GradeResponse.from(g));
    }

    private void applyRequest(Grade g, GradeRequest req) {
        g.setName(req.name());
        g.setDescription(req.description());
        g.setLevel(req.level());
        BigDecimal min = req.minSalary();
        BigDecimal max = req.maxSalary();
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BadRequestException("minSalary cannot exceed maxSalary");
        }
        g.setMinSalary(min);
        g.setMaxSalary(max);
        g.setCurrency(StringUtils.hasText(req.currency()) ? req.currency().toUpperCase() : "AZN");
        if (req.active() != null) g.setActive(req.active());
    }

    private static String normaliseCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }
}
