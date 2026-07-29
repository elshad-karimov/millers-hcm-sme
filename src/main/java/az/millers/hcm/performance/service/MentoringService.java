package az.millers.hcm.performance.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.access.AccessDeniedException;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.performance.domain.MentorProfile;
import az.millers.hcm.performance.domain.MentoringRelationship;
import az.millers.hcm.performance.repo.MentorProfileRepository;
import az.millers.hcm.performance.repo.MentoringRelationshipRepository;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * HCM_16 M417 — mentoring service (PRD §16.7).
 * Confidentiality: only mentor/mentee/HR see a relationship.
 */
@Service
public class MentoringService {

    private static final String MODULE = "MENTORING";

    private final MentorProfileRepository profiles;
    private final MentoringRelationshipRepository relationships;
    private final AccessScopeService accessScope;
    private final EmployeeContextService empContext;
    private final CurrentRequest currentRequest;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;

    public MentoringService(MentorProfileRepository profiles,
                             MentoringRelationshipRepository relationships,
                             AccessScopeService accessScope,
                             EmployeeContextService empContext,
                             CurrentRequest currentRequest,
                             NamedParameterJdbcTemplate jdbc,
                             AuditService audit) {
        this.profiles = profiles;
        this.relationships = relationships;
        this.accessScope = accessScope;
        this.empContext = empContext;
        this.currentRequest = currentRequest;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** Register as mentor (HR or the employee themself). */
    @Transactional
    public MentorProfileResponse registerMentor(MentorProfileRequest req) {
        if (req.employeeId == null) {
            throw new BadRequestException("employeeId required");
        }
        // Access guard: HR or the employee themself
        boolean isHR = currentRequest.hasRole("HR_ADMIN") || currentRequest.hasRole("HR_SPECIALIST");
        if (!isHR && !accessScope.isAccessible(req.employeeId)) {
            throw new AccessDeniedException("Cannot register mentor for another employee");
        }

        String tenant = TenantContext.current();
        if (profiles.findByTenantIdAndEmployeeId(tenant, req.employeeId).isPresent()) {
            throw new BadRequestException("Mentor profile already exists for this employee");
        }

        MentorProfile profile = new MentorProfile();
        profile.setEmployeeId(req.employeeId);
        profile.setAreas(req.areas);
        profile.setMaxMentees(req.maxMentees != null ? req.maxMentees : 3);
        profile.setActive(req.active != null ? req.active : true);
        profiles.save(profile);

        audit.record(MODULE, "MentorProfile", profile.getId().toString(), "REGISTER", null, null);
        return toProfileResponse(profile);
    }

    /** List active mentors (all authenticated users see them — transparent). */
    @Transactional(readOnly = true)
    public List<MentorProfileResponse> listMentors() {
        String tenant = TenantContext.current();
        return profiles.findByTenantIdAndActiveTrueOrderByCreatedAtDesc(tenant).stream()
                .map(this::toProfileResponse)
                .toList();
    }

    /** Request mentoring (mentee self-service). */
    @Transactional
    public RelationshipResponse request(UUID mentorProfileId) {
        UUID menteeId = empContext.currentEmployee().getId();

        MentorProfile mentor = profiles.findById(mentorProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentor profile not found: " + mentorProfileId));

        // Check if already requested/active
        List<MentoringRelationship> existing = relationships.findActiveOrRequestedForPair("default", menteeId, mentor.getEmployeeId());
        if (!existing.isEmpty()) {
            throw new BadRequestException("Already have an active/requested relationship with this mentor");
        }

        MentoringRelationship rel = new MentoringRelationship();
        rel.setMentorEmployeeId(mentor.getEmployeeId());
        rel.setMenteeEmployeeId(menteeId);
        rel.setStartDate(LocalDate.now());
        rel.setEndDate(LocalDate.now().plusMonths(6));
        rel.setStatus("REQUESTED");
        relationships.save(rel);

        audit.record(MODULE, "MentoringRelationship", rel.getId().toString(), "REQUEST", null, null);
        return toRelationshipResponse(rel);
    }

    /** Approve mentoring request (mentor or HR). */
    @Transactional
    public RelationshipResponse approve(UUID relationshipId) {
        MentoringRelationship rel = relationships.findById(relationshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Relationship not found: " + relationshipId));

        if (!"REQUESTED".equals(rel.getStatus())) {
            throw new BadRequestException("Only REQUESTED relationships can be approved");
        }

        // Access guard: mentor or HR
        boolean isHR = currentRequest.hasRole("HR_ADMIN") || currentRequest.hasRole("HR_SPECIALIST");
        boolean isMentor = accessScope.isAccessible(rel.getMentorEmployeeId());
        if (!isHR && !isMentor) {
            throw new AccessDeniedException("Only mentor or HR can approve");
        }

        // Capacity check with pessimistic lock to prevent race
        MentorProfile profile = profiles.findByTenantIdAndEmployeeId(TenantContext.current(), rel.getMentorEmployeeId())
                .orElseThrow(() -> new BadRequestException("Mentor profile not found"));
        // Lock the profile row to prevent concurrent approvals
        profiles.lockById(profile.getId())
                .orElseThrow(() -> new BadRequestException("Mentor profile not found"));
        long activeCount = relationships.countActiveMenteesByMentor("default", rel.getMentorEmployeeId());
        if (activeCount >= profile.getMaxMentees()) {
            throw new BadRequestException("Mentor at capacity (" + activeCount + "/" + profile.getMaxMentees() + ")");
        }

        rel.setStatus("ACTIVE");
        relationships.save(rel);
        audit.record(MODULE, "MentoringRelationship", relationshipId.toString(), "APPROVE", "REQUESTED", "ACTIVE");
        return toRelationshipResponse(rel);
    }

    /** Complete relationship (mentor/mentee/HR). */
    @Transactional
    public RelationshipResponse complete(UUID relationshipId) {
        MentoringRelationship rel = mustFind(relationshipId);
        if (!"ACTIVE".equals(rel.getStatus())) {
            throw new BadRequestException("Only ACTIVE relationships can be completed");
        }
        rel.setStatus("COMPLETED");
        relationships.save(rel);
        audit.record(MODULE, "MentoringRelationship", relationshipId.toString(), "COMPLETE", "ACTIVE", "COMPLETED");
        return toRelationshipResponse(rel);
    }

    /** Cancel relationship (anyone involved). */
    @Transactional
    public RelationshipResponse cancel(UUID relationshipId) {
        MentoringRelationship rel = mustFind(relationshipId);
        if ("CANCELLED".equals(rel.getStatus()) || "COMPLETED".equals(rel.getStatus())) {
            throw new BadRequestException("Cannot cancel a " + rel.getStatus() + " relationship");
        }
        String oldStatus = rel.getStatus();
        rel.setStatus("CANCELLED");
        relationships.save(rel);
        audit.record(MODULE, "MentoringRelationship", relationshipId.toString(), "CANCEL", oldStatus, "CANCELLED");
        return toRelationshipResponse(rel);
    }

    /** Update meeting notes (mentor/mentee/HR). */
    @Transactional
    public RelationshipResponse updateNotes(UUID relationshipId, String notes) {
        MentoringRelationship rel = mustFind(relationshipId);
        rel.setMeetingNotes(notes);
        relationships.save(rel);
        audit.record(MODULE, "MentoringRelationship", relationshipId.toString(), "NOTES_UPDATE", null, null);
        return toRelationshipResponse(rel);
    }

    /** List relationships (filtered: HR sees all, others see only where they're mentor or mentee). */
    @Transactional(readOnly = true)
    public List<RelationshipResponse> listRelationships() {
        boolean isHR = currentRequest.hasRole("HR_ADMIN") || currentRequest.hasRole("HR_SPECIALIST");
        if (isHR) {
            // HR sees all
            return relationships.findByTenantIdOrderByCreatedAtDesc(TenantContext.current()).stream()
                    .map(this::toRelationshipResponse)
                    .toList();
        } else {
            // Non-HR: only see where they're mentor or mentee
            UUID empId = empContext.currentEmployee().getId();
            return relationships.findByTenantIdAndMentorEmployeeIdOrMenteeEmployeeId(TenantContext.current(), empId, empId).stream()
                    .map(this::toRelationshipResponse)
                    .toList();
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private MentoringRelationship mustFind(UUID id) {
        MentoringRelationship rel = relationships.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Relationship not found: " + id));
        // Visibility guard
        boolean isHR = currentRequest.hasRole("HR_ADMIN") || currentRequest.hasRole("HR_SPECIALIST");
        if (!isHR) {
            UUID empId = empContext.currentEmployee().getId();
            if (!rel.getMentorEmployeeId().equals(empId) && !rel.getMenteeEmployeeId().equals(empId)) {
                throw new AccessDeniedException("Not visible");
            }
        }
        return rel;
    }

    private MentorProfileResponse toProfileResponse(MentorProfile p) {
        String name = loadEmployeeName(p.getEmployeeId());
        return new MentorProfileResponse(p.getId(), p.getEmployeeId(), name, p.getAreas(), p.getMaxMentees(), p.getActive());
    }

    private RelationshipResponse toRelationshipResponse(MentoringRelationship r) {
        String mentorName = loadEmployeeName(r.getMentorEmployeeId());
        String menteeName = loadEmployeeName(r.getMenteeEmployeeId());
        return new RelationshipResponse(
                r.getId(),
                r.getMentorEmployeeId(),
                mentorName,
                r.getMenteeEmployeeId(),
                menteeName,
                r.getStartDate(),
                r.getEndDate(),
                r.getStatus(),
                r.getMeetingNotes()
        );
    }

    private String loadEmployeeName(UUID empId) {
        var params = new MapSqlParameterSource("id", empId);
        List<String> names = jdbc.query(
                "SELECT first_name || ' ' || last_name AS name FROM core_hr.employee WHERE id = :id AND tenant_id = 'default'",
                params,
                (rs, i) -> rs.getString("name")
        );
        return names.isEmpty() ? null : names.get(0);
    }

    // ── DTOs ────────────────────────────────────────────────────────────────

    public record MentorProfileRequest(UUID employeeId, String areas, Integer maxMentees, Boolean active) {}
    public record MentorProfileResponse(UUID id, UUID employeeId, String name, String areas, Integer maxMentees, Boolean active) {}
    public record RelationshipResponse(
            UUID id,
            UUID mentorEmployeeId,
            String mentorName,
            UUID menteeEmployeeId,
            String menteeName,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String meetingNotes) {}
}
