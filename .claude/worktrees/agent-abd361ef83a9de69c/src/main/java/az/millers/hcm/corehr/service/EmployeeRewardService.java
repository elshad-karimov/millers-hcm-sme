package az.millers.hcm.corehr.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.RewardRequest;
import az.millers.hcm.corehr.api.dto.RewardResponse;
import az.millers.hcm.corehr.domain.EmployeeReward;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.corehr.repo.EmployeeRewardRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * CRUD for {@link EmployeeReward} (M72 / P2-18). Plain audit-backed CRUD —
 * no workflow, no PII masking. Rewards are explicitly positive records and
 * visible to anyone with access to the parent employee.
 */
@Service
public class EmployeeRewardService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeReward";

    private final EmployeeRewardRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;

    public EmployeeRewardService(EmployeeRewardRepository repository,
                                  EmployeeRepository employees,
                                  AuditService audit,
                                  AccessScopeService accessScope,
                                  CurrentRequest currentRequest) {
        this.repository = repository;
        this.employees = employees;
        this.audit = audit;
        this.accessScope = accessScope;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<RewardResponse> listFor(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        return repository.findByEmployeeIdOrderByAwardedAtDesc(employeeId)
                .stream().map(RewardResponse::from).toList();
    }

    @Transactional
    public RewardResponse create(UUID employeeId, RewardRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        EmployeeReward r = new EmployeeReward();
        r.setEmployeeId(employeeId);
        apply(r, req);
        r.setAwardedBy(currentRequest.username());
        r.setCreatedBy(currentRequest.username());
        r.setUpdatedBy(currentRequest.username());
        EmployeeReward saved = repository.save(r);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, RewardResponse.from(saved));
        return RewardResponse.from(saved);
    }

    @Transactional
    public RewardResponse update(UUID id, RewardRequest req) {
        EmployeeReward r = loadOrThrow(id);
        RewardResponse before = RewardResponse.from(r);
        apply(r, req);
        r.setUpdatedBy(currentRequest.username());
        EmployeeReward saved = repository.save(r);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, RewardResponse.from(saved));
        return RewardResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        EmployeeReward r = loadOrThrow(id);
        RewardResponse before = RewardResponse.from(r);
        repository.delete(r);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", before, null);
    }

    private void apply(EmployeeReward r, RewardRequest req) {
        r.setRewardType(req.rewardType());
        r.setTitle(req.title());
        r.setDescription(req.description());
        r.setAwardValue(req.awardValue());
        r.setCurrency(req.currency());
        r.setAwardedAt(req.awardedAt());
        r.setCertificateUrl(req.certificateUrl());
        r.setNotes(req.notes());
    }

    private EmployeeReward loadOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Reward not found: " + id));
    }

    private void ensureEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }
}
