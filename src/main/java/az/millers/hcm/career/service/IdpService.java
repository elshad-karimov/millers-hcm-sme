package az.millers.hcm.career.service;

import az.millers.hcm.career.api.dto.*;
import az.millers.hcm.career.domain.Idp;
import az.millers.hcm.career.domain.IdpActivity;
import az.millers.hcm.career.domain.IdpSkillGap;
import az.millers.hcm.career.repo.IdpRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Individual Development Plan service — M57 (Career-path / training plan).
 *
 * <p>One ACTIVE or DRAFT IDP per employee at a time. An employee (or HR) can
 * create a plan, add skill gaps and activities, activate it, then mark it
 * completed. Completed IDPs are retained; new ones can be started after completion.
 */
@Service
public class IdpService {

    private static final String DRAFT      = "DRAFT";
    private static final String ACTIVE     = "ACTIVE";
    private static final String COMPLETED  = "COMPLETED";
    private static final String CANCELLED  = "CANCELLED";

    private final IdpRepository idps;
    private final CurrentRequest currentRequest;

    public IdpService(IdpRepository idps, CurrentRequest currentRequest) {
        this.idps = idps;
        this.currentRequest = currentRequest;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // IDP CRUD
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public IdpResponse create(UUID employeeId, IdpRequest req) {
        // Only one open IDP per employee
        idps.findByEmployeeIdAndStatus(employeeId, DRAFT)
                .ifPresent(e -> { throw new BadRequestException("Employee already has a DRAFT IDP: " + e.getId()); });
        idps.findByEmployeeIdAndStatus(employeeId, ACTIVE)
                .ifPresent(e -> { throw new BadRequestException("Employee already has an ACTIVE IDP: " + e.getId()); });

        Idp idp = new Idp();
        idp.setEmployeeId(employeeId);
        idp.setTargetRole(req.targetRole());
        idp.setTargetDate(req.targetDate());
        idp.setCreatedBy(currentRequest.username());

        // Seed skill gaps
        if (req.skillGaps() != null) {
            for (SkillGapRequest sg : req.skillGaps()) {
                idp.getSkillGaps().add(toSkillGap(sg, idp));
            }
        }
        // Seed activities
        if (req.activities() != null) {
            for (ActivityRequest ar : req.activities()) {
                idp.getActivities().add(toActivity(ar, idp));
            }
        }

        return IdpResponse.from(idps.save(idp));
    }

    @Transactional(readOnly = true)
    public List<IdpResponse> list(UUID employeeId) {
        return idps.findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .stream().map(IdpResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public IdpResponse get(UUID idpId) {
        return IdpResponse.from(find(idpId));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle transitions
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public IdpResponse activate(UUID idpId) {
        Idp idp = find(idpId);
        require(idp, DRAFT, "activate");
        idp.setStatus(ACTIVE);
        return IdpResponse.from(idps.save(idp));
    }

    @Transactional
    public IdpResponse complete(UUID idpId) {
        Idp idp = find(idpId);
        require(idp, ACTIVE, "complete");
        idp.setStatus(COMPLETED);
        return IdpResponse.from(idps.save(idp));
    }

    @Transactional
    public IdpResponse cancel(UUID idpId) {
        Idp idp = find(idpId);
        if (COMPLETED.equals(idp.getStatus())) {
            throw new BadRequestException("Cannot cancel a COMPLETED IDP");
        }
        idp.setStatus(CANCELLED);
        return IdpResponse.from(idps.save(idp));
    }

    @Transactional
    public IdpResponse addManagerComment(UUID idpId, String comment) {
        Idp idp = find(idpId);
        idp.setManagerComment(comment);
        return IdpResponse.from(idps.save(idp));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Skill gaps
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public IdpResponse addSkillGap(UUID idpId, SkillGapRequest req) {
        Idp idp = find(idpId);
        guardEditable(idp);
        idp.getSkillGaps().add(toSkillGap(req, idp));
        return IdpResponse.from(idps.save(idp));
    }

    @Transactional
    public IdpResponse removeSkillGap(UUID idpId, UUID gapId) {
        Idp idp = find(idpId);
        guardEditable(idp);
        idp.getSkillGaps().removeIf(g -> g.getId().equals(gapId));
        return IdpResponse.from(idps.save(idp));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Activities
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public IdpResponse addActivity(UUID idpId, ActivityRequest req) {
        Idp idp = find(idpId);
        guardEditable(idp);
        idp.getActivities().add(toActivity(req, idp));
        return IdpResponse.from(idps.save(idp));
    }

    @Transactional
    public IdpResponse updateActivityStatus(UUID idpId, UUID activityId, String status) {
        Idp idp = find(idpId);
        IdpActivity act = idp.getActivities().stream()
                .filter(a -> a.getId().equals(activityId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Activity not found: " + activityId));
        act.setStatus(status);
        if ("DONE".equals(status)) act.setCompletedAt(LocalDate.now());
        return IdpResponse.from(idps.save(idp));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internals
    // ──────────────────────────────────────────────────────────────────────────

    private Idp find(UUID id) {
        return idps.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IDP not found: " + id));
    }

    private static void require(Idp idp, String expected, String op) {
        if (!expected.equals(idp.getStatus())) {
            throw new BadRequestException(
                    "Cannot " + op + " IDP in status " + idp.getStatus() + " (requires " + expected + ")");
        }
    }

    private static void guardEditable(Idp idp) {
        if (COMPLETED.equals(idp.getStatus()) || CANCELLED.equals(idp.getStatus())) {
            throw new BadRequestException("IDP is " + idp.getStatus() + " and cannot be modified");
        }
    }

    private static IdpSkillGap toSkillGap(SkillGapRequest req, Idp idp) {
        IdpSkillGap gap = new IdpSkillGap();
        gap.setIdp(idp);
        gap.setCompetencyId(req.competencyId());
        gap.setSkillName(req.skillName());
        gap.setCurrentLevel(req.currentLevel());
        gap.setTargetLevel(req.targetLevel());
        gap.setNotes(req.notes());
        return gap;
    }

    private static IdpActivity toActivity(ActivityRequest req, Idp idp) {
        IdpActivity act = new IdpActivity();
        act.setIdp(idp);
        act.setTitle(req.title());
        if (req.activityType() != null) act.setActivityType(req.activityType());
        act.setCourseId(req.courseId());
        act.setDueDate(req.dueDate());
        act.setNotes(req.notes());
        return act;
    }
}
