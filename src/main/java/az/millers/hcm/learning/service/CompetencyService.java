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
import az.millers.hcm.learning.domain.Competency;
import az.millers.hcm.learning.domain.EmployeeCompetency;
import az.millers.hcm.learning.repo.CompetencyRepository;
import az.millers.hcm.learning.repo.EmployeeCompetencyRepository;

@Service
public class CompetencyService {

    private static final String MODULE = "LEARNING";
    private static final String ENTITY = "Competency";

    private final CompetencyRepository competencies;
    private final EmployeeCompetencyRepository employeeCompetencies;
    private final AuditService audit;

    public CompetencyService(CompetencyRepository competencies,
                              EmployeeCompetencyRepository employeeCompetencies,
                              AuditService audit) {
        this.competencies = competencies;
        this.employeeCompetencies = employeeCompetencies;
        this.audit = audit;
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
}
