package az.millers.hcm.recruitment.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.CandidateRequest;
import az.millers.hcm.recruitment.api.dto.CandidateResponse;
import az.millers.hcm.recruitment.domain.Candidate;
import az.millers.hcm.recruitment.repo.CandidateRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class CandidateService {

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "Candidate";

    private final CandidateRepository repository;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public CandidateService(CandidateRepository repository, AuditService audit,
                             CurrentRequest currentRequest) {
        this.repository = repository;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public Candidate get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Candidate> list(String search, Pageable pageable) {
        // M293 — merged-away candidates drop out of the active list.
        if (StringUtils.hasText(search)) {
            return repository.searchActive(search, pageable);
        }
        return repository.findByMergedIntoIdIsNullOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public Candidate create(CandidateRequest req) {
        Candidate c = new Candidate();
        c.setCandidateNo(String.format("CAND-%05d", repository.nextNoSequence()));
        c.setCreatedBy(currentRequest.username());
        c.setUpdatedBy(currentRequest.username());
        applyRequest(c, req);
        Candidate saved = repository.save(c);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, CandidateResponse.from(saved));
        return saved;
    }

    @Transactional
    public Candidate update(UUID id, CandidateRequest req) {
        Candidate c = get(id);
        CandidateResponse before = CandidateResponse.from(c);
        applyRequest(c, req);
        c.setUpdatedBy(currentRequest.username());
        Candidate saved = repository.save(c);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, CandidateResponse.from(saved));
        return saved;
    }

    private void applyRequest(Candidate c, CandidateRequest req) {
        c.setFirstName(req.firstName());
        c.setLastName(req.lastName());
        c.setMiddleName(req.middleName());
        c.setEmail(req.email());
        c.setPhone(req.phone());
        c.setSource(req.source());
        c.setCvUrl(req.cvUrl());
        c.setExperienceYears(req.experienceYears());
        c.setExpectedSalary(req.expectedSalary());
        c.setCurrency(req.currency() == null ? "AZN" : req.currency().toUpperCase());
        c.setSkills(req.skills());
        c.setNotes(req.notes());
    }
}
