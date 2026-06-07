package az.millers.hcm.learning.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.learning.api.dto.CompetencyRequest;
import az.millers.hcm.learning.api.dto.CompetencyResponse;
import az.millers.hcm.learning.api.dto.EmployeeCompetencyResponse;
import az.millers.hcm.learning.api.dto.EndorsementRequest;
import az.millers.hcm.learning.domain.Competency;
import az.millers.hcm.learning.domain.EmployeeCompetency;
import az.millers.hcm.learning.repo.CompetencyRepository;
import az.millers.hcm.learning.repo.EmployeeCompetencyRepository;
import az.millers.hcm.selfservice.service.EmployeeContextService;

@Service
public class CompetencyService {

    private static final String MODULE = "LEARNING";
    private static final String ENTITY = "Competency";
    private static final String EC_ENTITY = "EmployeeCompetency";

    private final CompetencyRepository competencies;
    private final EmployeeCompetencyRepository employeeCompetencies;
    private final AuditService audit;
    /** M136 — resolves the endorser from the auth context. */
    private final EmployeeContextService employeeContext;

    public CompetencyService(CompetencyRepository competencies,
                              EmployeeCompetencyRepository employeeCompetencies,
                              AuditService audit,
                              EmployeeContextService employeeContext) {
        this.competencies = competencies;
        this.employeeCompetencies = employeeCompetencies;
        this.audit = audit;
        this.employeeContext = employeeContext;
    }

    @Transactional(readOnly = true)
    public List<Competency> list(boolean activeOnly) {
        return activeOnly
                ? competencies.findByActiveTrueOrderByNameAsc()
                : competencies.findAll();
    }

    @Transactional(readOnly = true)
    public Competency get(UUID id) {
        return competencies.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Competency not found: " + id));
    }

    @Transactional
    public Competency create(CompetencyRequest req) {
        if (competencies.existsByCode(req.code())) {
            throw new BadRequestException("Competency code already exists: " + req.code());
        }
        Competency c = new Competency();
        apply(c, req);
        Competency saved = competencies.save(c);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, CompetencyResponse.from(saved));
        return saved;
    }

    @Transactional
    public Competency update(UUID id, CompetencyRequest req) {
        Competency c = get(id);
        CompetencyResponse before = CompetencyResponse.from(c);
        apply(c, req);
        Competency saved = competencies.save(c);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, CompetencyResponse.from(saved));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EmployeeCompetency> forEmployee(UUID employeeId) {
        return employeeCompetencies.findByEmployeeIdOrderByAwardedAtDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public List<EmployeeCompetency> forCompetency(UUID competencyId) {
        return employeeCompetencies.findByCompetencyIdOrderByAwardedAtDesc(competencyId);
    }

    private void apply(Competency c, CompetencyRequest req) {
        c.setCode(req.code());
        c.setName(req.name());
        c.setDescription(req.description());
        c.setCategory(req.category());
        c.setActive(req.active() == null ? true : req.active());
    }

    // ── M136 — years of experience + endorsement ──────────────────────

    /**
     * Update {@code yearsOfExperience} on an employee_competency.
     * Passing null clears the field. The value is bounded by the DB
     * CHECK ({@code >= 0}); reject negatives up-front for a clean error.
     */
    @Transactional
    public EmployeeCompetency setYearsOfExperience(UUID employeeCompetencyId,
                                                    java.math.BigDecimal years) {
        EmployeeCompetency ec = employeeCompetencies.findById(employeeCompetencyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee competency not found: " + employeeCompetencyId));
        if (years != null && years.signum() < 0) {
            throw new BadRequestException("yearsOfExperience must be >= 0");
        }
        EmployeeCompetencyResponse before = EmployeeCompetencyResponse.from(ec);
        ec.setYearsOfExperience(years);
        EmployeeCompetency saved = employeeCompetencies.save(ec);
        audit.record(MODULE, EC_ENTITY, saved.getId().toString(),
                "YEARS_UPDATE", before, EmployeeCompetencyResponse.from(saved));
        return saved;
    }

    /**
     * Record a manager / mentor endorsement. The endorser identity is
     * pulled from the auth context — never accepted from the request
     * body — so a user can't endorse on behalf of someone else.
     *
     * <p>When {@code endorsedLevel} differs from the current
     * {@code proficiency}, the endorsement is treated as authoritative
     * and the proficiency is updated to match. This handles both
     * upgrades (manager confirms a higher level than the self-rating)
     * and downgrades (manager corrects an inflated self-rating).
     */
    @Transactional
    public EmployeeCompetency endorse(UUID employeeCompetencyId, EndorsementRequest req) {
        EmployeeCompetency ec = employeeCompetencies.findById(employeeCompetencyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee competency not found: " + employeeCompetencyId));
        UUID endorserId = employeeContext.currentEmployee().getId();
        if (endorserId.equals(ec.getEmployeeId())) {
            throw new BadRequestException("Cannot endorse your own competency");
        }
        EmployeeCompetencyResponse before = EmployeeCompetencyResponse.from(ec);
        ec.setEndorsedByEmployeeId(endorserId);
        ec.setEndorsedAt(java.time.OffsetDateTime.now());
        ec.setEndorsedLevel(req.endorsedLevel());
        ec.setEndorsementNote(req.note());
        // Endorser's call wins — update the canonical proficiency too.
        ec.setProficiency(req.endorsedLevel());
        EmployeeCompetency saved = employeeCompetencies.save(ec);
        audit.record(MODULE, EC_ENTITY, saved.getId().toString(),
                "ENDORSE", before, EmployeeCompetencyResponse.from(saved));
        return saved;
    }
}
