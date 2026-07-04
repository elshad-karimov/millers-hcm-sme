package az.millers.hcm.staffing.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.api.dto.JobFamilyRequest;
import az.millers.hcm.staffing.api.dto.JobFamilyResponse;
import az.millers.hcm.staffing.domain.JobFamily;
import az.millers.hcm.staffing.repo.JobFamilyRepository;

/**
 * Master CRUD for {@link JobFamily} — high-level role grouping referenced by
 * {@code Position.jobFamilyId} (M75 / P2-19). Same uppercase code rules as Grade.
 */
@Service
public class JobFamilyService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "JobFamily";

    private final JobFamilyRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public JobFamilyService(JobFamilyRepository repo,
                            AuditService audit,
                            CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<JobFamily> list(boolean activeOnly) {
        return activeOnly
                ? repo.findByActiveTrueOrderByNameAsc()
                : repo.findAll();
    }

    @Transactional(readOnly = true)
    public JobFamily get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JobFamily not found: " + id));
    }

    @Transactional
    public JobFamily create(JobFamilyRequest req) {
        String code = normaliseCode(req.code());
        if (repo.findByCode(code).isPresent()) {
            throw new BadRequestException("JobFamily code already exists: " + code);
        }
        JobFamily f = new JobFamily();
        f.setCode(code);
        applyRequest(f, req);
        f.setCreatedBy(currentRequest.username());
        f.setUpdatedBy(currentRequest.username());
        JobFamily saved = repo.save(f);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, JobFamilyResponse.from(saved));
        return saved;
    }

    @Transactional
    public JobFamily update(UUID id, JobFamilyRequest req) {
        JobFamily f = get(id);
        JobFamilyResponse before = JobFamilyResponse.from(f);
        if (!f.getCode().equalsIgnoreCase(req.code())) {
            throw new BadRequestException("JobFamily code is immutable");
        }
        applyRequest(f, req);
        f.setUpdatedBy(currentRequest.username());
        JobFamily saved = repo.save(f);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, JobFamilyResponse.from(saved));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        JobFamily f = get(id);
        JobFamilyResponse before = JobFamilyResponse.from(f);
        f.setActive(false);
        f.setUpdatedBy(currentRequest.username());
        repo.save(f);
        audit.record(MODULE, ENTITY, id.toString(),
                "DEACTIVATE", before, JobFamilyResponse.from(f));
    }

    private void applyRequest(JobFamily f, JobFamilyRequest req) {
        f.setName(req.name());
        f.setDescription(req.description());
        if (req.active() != null) f.setActive(req.active());
    }

    private static String normaliseCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }
}
