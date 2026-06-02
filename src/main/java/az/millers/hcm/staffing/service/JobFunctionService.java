package az.millers.hcm.staffing.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.api.dto.JobFunctionRequest;
import az.millers.hcm.staffing.api.dto.JobFunctionResponse;
import az.millers.hcm.staffing.domain.JobFunction;
import az.millers.hcm.staffing.repo.JobFamilyRepository;
import az.millers.hcm.staffing.repo.JobFunctionRepository;

/**
 * Master CRUD for {@link JobFunction} — narrower than JobFamily, optional
 * soft FK back to {@link az.millers.hcm.staffing.domain.JobFamily}. Used by
 * {@code Position.jobFunctionId} (M75 / P2-19).
 */
@Service
public class JobFunctionService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "JobFunction";

    private final JobFunctionRepository repo;
    private final JobFamilyRepository familyRepo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public JobFunctionService(JobFunctionRepository repo,
                              JobFamilyRepository familyRepo,
                              AuditService audit,
                              CurrentRequest currentRequest) {
        this.repo = repo;
        this.familyRepo = familyRepo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<JobFunction> list(UUID jobFamilyId, boolean activeOnly) {
        if (jobFamilyId != null) {
            return repo.findByJobFamilyIdAndActiveTrueOrderByNameAsc(jobFamilyId);
        }
        return activeOnly
                ? repo.findByActiveTrueOrderByNameAsc()
                : repo.findAll();
    }

    @Transactional(readOnly = true)
    public JobFunction get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JobFunction not found: " + id));
    }

    @Transactional
    public JobFunction create(JobFunctionRequest req) {
        String code = normaliseCode(req.code());
        if (repo.findByCode(code).isPresent()) {
            throw new BadRequestException("JobFunction code already exists: " + code);
        }
        validateFamily(req.jobFamilyId());
        JobFunction f = new JobFunction();
        f.setCode(code);
        applyRequest(f, req);
        f.setCreatedBy(currentRequest.username());
        f.setUpdatedBy(currentRequest.username());
        JobFunction saved = repo.save(f);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, JobFunctionResponse.from(saved));
        return saved;
    }

    @Transactional
    public JobFunction update(UUID id, JobFunctionRequest req) {
        JobFunction f = get(id);
        JobFunctionResponse before = JobFunctionResponse.from(f);
        if (!f.getCode().equalsIgnoreCase(req.code())) {
            throw new BadRequestException("JobFunction code is immutable");
        }
        validateFamily(req.jobFamilyId());
        applyRequest(f, req);
        f.setUpdatedBy(currentRequest.username());
        JobFunction saved = repo.save(f);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, JobFunctionResponse.from(saved));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        JobFunction f = get(id);
        JobFunctionResponse before = JobFunctionResponse.from(f);
        f.setActive(false);
        f.setUpdatedBy(currentRequest.username());
        repo.save(f);
        audit.record(MODULE, ENTITY, id.toString(),
                "DEACTIVATE", before, JobFunctionResponse.from(f));
    }

    private void validateFamily(UUID jobFamilyId) {
        if (jobFamilyId != null && !familyRepo.existsById(jobFamilyId)) {
            throw new BadRequestException("jobFamilyId references unknown JobFamily: " + jobFamilyId);
        }
    }

    private void applyRequest(JobFunction f, JobFunctionRequest req) {
        f.setName(req.name());
        f.setDescription(req.description());
        f.setJobFamilyId(req.jobFamilyId());
        if (req.active() != null) f.setActive(req.active());
    }

    private static String normaliseCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }
}
