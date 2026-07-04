package az.millers.hcm.performance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.domain.EmployeePoolMember;
import az.millers.hcm.performance.domain.EmployeeTalentPool;
import az.millers.hcm.performance.repo.EmployeePoolMemberRepository;
import az.millers.hcm.performance.repo.EmployeeTalentPoolRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;

/**
 * HCM_15 M410 — employee talent pools (PRD §15.5/§15.6). Thin CRUD.
 * HR-only: employees NEVER see talent-pool membership (GLOBAL RULE 17).
 */
@RestController
@RequestMapping("/api/talent/pools")
public class EmployeeTalentPoolController {

    private static final String TENANT = "default";
    private static final String MODULE = "TALENT";

    public record PoolRequest(String code, String name, String purpose,
                              UUID ownerEmployeeId, Boolean active) {}

    public record MemberRequest(UUID employeeId, String note) {}

    private final EmployeeTalentPoolRepository pools;
    private final EmployeePoolMemberRepository members;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public EmployeeTalentPoolController(EmployeeTalentPoolRepository pools,
                                        EmployeePoolMemberRepository members,
                                        EmployeeRepository employees,
                                        AuditService audit,
                                        CurrentRequest currentRequest) {
        this.pools = pools;
        this.members = members;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Pools ───────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<EmployeeTalentPool> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return activeOnly
                ? pools.findByTenantIdAndActiveTrueOrderByCodeAsc(TENANT)
                : pools.findByTenantIdOrderByCodeAsc(TENANT);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public EmployeeTalentPool get(@PathVariable UUID id) {
        return pools.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Talent pool not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    @Transactional
    public EmployeeTalentPool create(@RequestBody PoolRequest req) {
        if (req.code() == null || req.code().isBlank()
                || req.name() == null || req.name().isBlank()) {
            throw new BadRequestException("Pool code and name are required");
        }
        String code = req.code().trim().toUpperCase();
        if (pools.existsByTenantIdAndCode(TENANT, code)) {
            throw new BadRequestException("Pool code already exists: " + code);
        }
        if (req.ownerEmployeeId() != null && !employees.existsById(req.ownerEmployeeId())) {
            throw new BadRequestException("Owner employee not found: " + req.ownerEmployeeId());
        }
        EmployeeTalentPool pool = new EmployeeTalentPool();
        pool.setTenantId(TENANT);
        pool.setCode(code);
        pool.setName(req.name().trim());
        pool.setPurpose(req.purpose());
        pool.setOwnerEmployeeId(req.ownerEmployeeId());
        if (req.active() != null) pool.setActive(req.active());
        pool.setCreatedBy(currentRequest.username());
        EmployeeTalentPool saved = pools.save(pool);
        audit.record(MODULE, "EmployeeTalentPool", saved.getId().toString(),
                "CREATE", null, code);
        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    @Transactional
    public EmployeeTalentPool update(@PathVariable UUID id, @RequestBody PoolRequest req) {
        EmployeeTalentPool pool = pools.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Talent pool not found: " + id));
        if (req.name() != null && !req.name().isBlank()) {
            pool.setName(req.name().trim());
        }
        if (req.purpose() != null) pool.setPurpose(req.purpose());
        if (req.ownerEmployeeId() != null) {
            if (!employees.existsById(req.ownerEmployeeId())) {
                throw new BadRequestException("Owner employee not found: " + req.ownerEmployeeId());
            }
            pool.setOwnerEmployeeId(req.ownerEmployeeId());
        }
        if (req.active() != null) pool.setActive(req.active());
        EmployeeTalentPool saved = pools.save(pool);
        audit.record(MODULE, "EmployeeTalentPool", id.toString(),
                "UPDATE", null, pool.getCode());
        return saved;
    }

    // ── Members ─────────────────────────────────────────────────────────────

    @GetMapping("/{poolId}/members")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<EmployeePoolMember> getMembers(@PathVariable UUID poolId) {
        if (!pools.existsById(poolId)) {
            throw new ResourceNotFoundException("Talent pool not found: " + poolId);
        }
        return members.findByTenantIdAndPoolIdOrderByAddedAtDesc(TENANT, poolId);
    }

    @PostMapping("/{poolId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    @Transactional
    public EmployeePoolMember addMember(@PathVariable UUID poolId, @RequestBody MemberRequest req) {
        if (!pools.existsById(poolId)) {
            throw new ResourceNotFoundException("Talent pool not found: " + poolId);
        }
        if (req.employeeId() == null || !employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        if (members.existsByTenantIdAndPoolIdAndEmployeeId(TENANT, poolId, req.employeeId())) {
            throw new BadRequestException("Employee is already in this pool");
        }
        EmployeePoolMember member = new EmployeePoolMember();
        member.setTenantId(TENANT);
        member.setPoolId(poolId);
        member.setEmployeeId(req.employeeId());
        member.setNote(req.note());
        member.setAddedBy(currentRequest.username());
        EmployeePoolMember saved = members.save(member);
        audit.record(MODULE, "EmployeePoolMember", saved.getId().toString(),
                "ADD", null, req.employeeId().toString());
        return saved;
    }

    @DeleteMapping("/{poolId}/members/{employeeId}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    @Transactional
    public void removeMember(@PathVariable UUID poolId, @PathVariable UUID employeeId) {
        if (!members.existsByTenantIdAndPoolIdAndEmployeeId(TENANT, poolId, employeeId)) {
            throw new ResourceNotFoundException("Member not found in this pool");
        }
        members.deleteByTenantIdAndPoolIdAndEmployeeId(TENANT, poolId, employeeId);
        audit.record(MODULE, "EmployeePoolMember", poolId + "/" + employeeId,
                "REMOVE", employeeId.toString(), null);
    }
}
