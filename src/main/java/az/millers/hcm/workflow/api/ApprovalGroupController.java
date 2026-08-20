package az.millers.hcm.workflow.api;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.admin.KeycloakAdminService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.workflow.domain.ApprovalGroup;
import az.millers.hcm.workflow.domain.ApprovalGroupMember;
import az.millers.hcm.workflow.repo.ApprovalGroupMemberRepository;
import az.millers.hcm.workflow.repo.ApprovalGroupRepository;

/**
 * M443 — Approval group admin endpoints.
 */
@RestController
@RequestMapping("/api/workflow/approval-groups")
public class ApprovalGroupController {


    private final ApprovalGroupRepository groupRepo;
    private final ApprovalGroupMemberRepository memberRepo;
    private final KeycloakAdminService keycloak;

    public ApprovalGroupController(ApprovalGroupRepository groupRepo,
                                  ApprovalGroupMemberRepository memberRepo,
                                  KeycloakAdminService keycloak) {
        this.groupRepo = groupRepo;
        this.memberRepo = memberRepo;
        this.keycloak = keycloak;
    }

    public record CreateGroupRequest(String code, String name) {}
    public record UpdateGroupRequest(String name, Boolean active) {}
    public record AddMemberRequest(String username) {}

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<ApprovalGroup> list() {
        return groupRepo.findByTenantIdAndActiveTrueOrderByNameAsc(TenantContext.current());
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public ApprovalGroup get(@PathVariable UUID id) {
        return groupRepo.findByIdAndTenantId(id, TenantContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("Approval group not found: " + id));
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ApprovalGroup create(@RequestBody CreateGroupRequest req) {
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();

        ApprovalGroup group = new ApprovalGroup();
        group.setId(UUID.randomUUID());
        group.setTenantId(TenantContext.current());
        group.setCode(req.code());
        group.setName(req.name());
        group.setActive(true);
        group.setCreatedAt(OffsetDateTime.now());
        group.setCreatedBy(currentUser);
        group.setUpdatedAt(OffsetDateTime.now());
        group.setUpdatedBy(currentUser);

        return groupRepo.save(group);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ApprovalGroup update(@PathVariable UUID id, @RequestBody UpdateGroupRequest req) {
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();

        ApprovalGroup group = get(id);

        if (req.name() != null) {
            group.setName(req.name());
        }
        if (req.active() != null) {
            group.setActive(req.active());
        }

        group.setUpdatedBy(currentUser);
        group.setUpdatedAt(OffsetDateTime.now());

        return groupRepo.save(group);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void delete(@PathVariable UUID id) {
        ApprovalGroup group = get(id);
        group.setActive(false);
        groupRepo.save(group);
    }

    @GetMapping("/{id}/members")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<ApprovalGroupMember> getMembers(@PathVariable UUID id) {
        return memberRepo.findByGroupIdOrderByUsername(id);
    }

    /**
     * Adds one approver to the group.
     *
     * <p>The username is checked against Keycloak before it is stored. It used
     * to be persisted verbatim — any string at all was accepted — and the
     * consequence is quiet rather than loud: a member row that matches no real
     * login is a seat in the approval chain that nobody can ever fill. The
     * request sits PENDING, waiting for an approver who cannot exist, and
     * nothing in the UI says so. Keycloak is the right authority here because
     * it is the same identity the approval path matches on — WorkflowService
     * compares these usernames against the authenticated principal's name.
     */
    @PostMapping("/{id}/members")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ApprovalGroupMember addMember(@PathVariable UUID id, @RequestBody AddMemberRequest req) {
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();

        // Verify group exists
        get(id);

        String username = req.username() == null ? "" : req.username().trim();
        if (username.isEmpty()) {
            throw new BadRequestException("A username is required.");
        }
        if (keycloak.findUserIdByUsername(username).isEmpty()) {
            throw new BadRequestException(
                    "No user account named '" + username + "' exists. An approval group "
                            + "member must be someone who can sign in, or the approvals "
                            + "routed to this group can never be actioned.");
        }
        boolean already = memberRepo.findByGroupIdOrderByUsername(id).stream()
                .anyMatch(m -> username.equalsIgnoreCase(m.getUsername()));
        if (already) {
            throw new BadRequestException("'" + username + "' is already a member of this group.");
        }

        ApprovalGroupMember member = new ApprovalGroupMember();
        member.setId(UUID.randomUUID());
        member.setTenantId(TenantContext.current());
        member.setGroupId(id);
        member.setUsername(username);
        member.setCreatedAt(OffsetDateTime.now());
        member.setCreatedBy(currentUser);

        return memberRepo.save(member);
    }

    @DeleteMapping("/members/{memberId}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void removeMember(@PathVariable UUID memberId) {
        ApprovalGroupMember member = memberRepo.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));

        if (!TenantContext.current().equals(member.getTenantId())) {
            throw new ResourceNotFoundException("Member not found: " + memberId);
        }

        memberRepo.delete(member);
    }
}
