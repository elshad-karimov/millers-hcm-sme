package az.millers.hcm.staffing.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.EmployeeAllowanceRequest;
import az.millers.hcm.compbenefits.domain.AllowanceType;
import az.millers.hcm.compbenefits.domain.EmployeeAllowance;
import az.millers.hcm.compbenefits.repo.AllowanceTypeRepository;
import az.millers.hcm.compbenefits.service.EmployeeAllowanceService;
import az.millers.hcm.corehr.api.dto.AssetRequest;
import az.millers.hcm.corehr.api.dto.AssetResponse;
import az.millers.hcm.corehr.api.dto.AssetReturnRequest;
import az.millers.hcm.corehr.domain.ApprovalLimitType;
import az.millers.hcm.corehr.domain.AssetStatus;
import az.millers.hcm.corehr.domain.AssetType;
import az.millers.hcm.corehr.domain.DocumentRequirementType;
import az.millers.hcm.corehr.domain.EmployeeApprovalLimit;
import az.millers.hcm.corehr.domain.RequiredEmployeeDocument;
import az.millers.hcm.corehr.service.EmployeeApprovalLimitService;
import az.millers.hcm.corehr.service.EmployeeAssetService;
import az.millers.hcm.corehr.service.RequiredEmployeeDocumentService;
import az.millers.hcm.admin.KeycloakAdminService;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.learning.api.dto.EnrollRequest;
import az.millers.hcm.learning.domain.Course;
import az.millers.hcm.learning.domain.CourseStatus;
import az.millers.hcm.learning.domain.EnrolledVia;
import az.millers.hcm.learning.domain.Enrollment;
import az.millers.hcm.learning.repo.CourseRepository;
import az.millers.hcm.learning.repo.EnrollmentRepository;
import az.millers.hcm.learning.service.EnrollmentService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.GrantStatus;
import az.millers.hcm.staffing.domain.PositionOccupancy;
import az.millers.hcm.staffing.domain.PositionProfileGrant;
import az.millers.hcm.staffing.domain.PositionProfileItem;
import az.millers.hcm.staffing.domain.ProfileItemType;
import az.millers.hcm.staffing.repo.PositionProfileGrantRepository;
import az.millers.hcm.staffing.repo.PositionProfileItemRepository;

/**
 * M250 — Phase F.2: grant lifecycle on a position profile.
 *
 * <p>Owns every write path on {@link PositionProfileGrant}. The main
 * entry point is {@link #autoGrantForOccupancy} — called by
 * {@link PositionOccupancyService} when a PRIMARY occupancy is created.
 * For each mandatory profile item on the position, this service writes
 * a PENDING grant row so HR sees the to-do list immediately.
 */
@Service
public class PositionProfileGrantService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "PositionProfileGrant";

    private final PositionProfileGrantRepository grants;
    private final PositionProfileItemRepository items;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    // M251 — Phase F.3: when an ALLOWANCE grant has a reference_code that
    // resolves to an AllowanceType.code, we auto-create the matching
    // employee_allowance row so payroll picks it up immediately.
    private final EmployeeAllowanceService employeeAllowanceService;
    private final AllowanceTypeRepository allowanceTypes;
    // M252 — Phase F.4: when a TRAINING grant has a reference_code that
    // resolves to a Course.code, we auto-enrol the employee in the
    // matching course. Idempotent — if the employee is already enrolled
    // we just link to the existing enrollment.
    private final EnrollmentService enrollmentService;
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    // M253 — Phase F.5b: when an EQUIPMENT grant has a reference_code that
    // matches one of the AssetType enum values, we auto-create the matching
    // employee_asset row + auto-return it on revoke.
    private final EmployeeAssetService employeeAssetService;
    // M261 — Phase F.7: when an APPROVAL_LIMIT grant has a reference_code
    // matching one of the ApprovalLimitType enum values + a value_amount,
    // we auto-create an effective-dated employee_approval_limit row and
    // effective-date it out on revoke.
    private final EmployeeApprovalLimitService approvalLimitService;
    // M262 — Phase F.5a: when a REQUIRED_DOCUMENT grant fires, we auto-
    // create a required_employee_document row so the employee shows up
    // on the "owes HR these documents" list. Waived on revoke.
    private final RequiredEmployeeDocumentService requiredDocService;
    // M265 — Phase F.6: when an ACCESS_ROLE grant fires, we look up the
    // employee's Keycloak user_id by username and grant the realm role
    // named in reference_code. Revoke removes it. Soft-fails when the
    // user has no Keycloak account yet (newly created hire) — operator
    // can mark the grant ACTIVE manually after provisioning.
    private final KeycloakAdminService keycloakAdminService;
    private final EmployeeRepository employees;

    public PositionProfileGrantService(PositionProfileGrantRepository grants,
                                        PositionProfileItemRepository items,
                                        AuditService audit,
                                        CurrentRequest currentRequest,
                                        EmployeeAllowanceService employeeAllowanceService,
                                        AllowanceTypeRepository allowanceTypes,
                                        EnrollmentService enrollmentService,
                                        EnrollmentRepository enrollments,
                                        CourseRepository courses,
                                        EmployeeAssetService employeeAssetService,
                                        EmployeeApprovalLimitService approvalLimitService,
                                        RequiredEmployeeDocumentService requiredDocService,
                                        KeycloakAdminService keycloakAdminService,
                                        EmployeeRepository employees) {
        this.grants = grants;
        this.items = items;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.employeeAllowanceService = employeeAllowanceService;
        this.allowanceTypes = allowanceTypes;
        this.enrollmentService = enrollmentService;
        this.enrollments = enrollments;
        this.courses = courses;
        this.employeeAssetService = employeeAssetService;
        this.approvalLimitService = approvalLimitService;
        this.requiredDocService = requiredDocService;
        this.keycloakAdminService = keycloakAdminService;
        this.employees = employees;
    }

    // ── Reads ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PositionProfileGrant> forOccupancy(UUID occupancyId) {
        return grants.findByOccupancyIdOrderByItemTypeAscLabelAsc(occupancyId);
    }

    @Transactional(readOnly = true)
    public List<PositionProfileGrant> pendingForEmployee(UUID employeeId) {
        return grants.findByEmployeeIdAndStatusOrderByCreatedAtDesc(employeeId, GrantStatus.PENDING);
    }

    // ── Auto-grant on occupancy create (called by PositionOccupancyService) ──

    /**
     * Create PENDING grants for every mandatory profile item on the
     * position. Idempotent — if the occupancy already has a grant for a
     * profile item, it isn't recreated. Safe to call on M249's
     * {@code openPrimary} re-run paths.
     */
    @Transactional
    public List<PositionProfileGrant> autoGrantForOccupancy(PositionOccupancy occupancy) {
        if (occupancy == null) return List.of();
        var profileItems = items.findByPositionIdOrderByItemTypeAscSortOrderAscLabelAsc(
                occupancy.getPositionId());
        var existing = grants.findByOccupancyIdOrderByItemTypeAscLabelAsc(occupancy.getId());
        var existingItemIds = existing.stream()
                .map(PositionProfileGrant::getProfileItemId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        java.util.List<PositionProfileGrant> created = new java.util.ArrayList<>();
        String actor = currentRequest.username();
        for (PositionProfileItem it : profileItems) {
            if (!it.isMandatory()) continue;          // optional items not auto-granted
            if (existingItemIds.contains(it.getId())) continue;  // idempotent

            PositionProfileGrant g = new PositionProfileGrant();
            g.setOccupancyId(occupancy.getId());
            g.setProfileItemId(it.getId());
            g.setEmployeeId(occupancy.getEmployeeId());
            g.setPositionId(occupancy.getPositionId());
            // Snapshot — copy at grant time.
            g.setItemType(it.getItemType());
            g.setLabel(it.getLabel());
            g.setValueAmount(it.getValueAmount());
            g.setCurrency(it.getCurrency());
            g.setReferenceCode(it.getReferenceCode());
            g.setNotes(it.getNotes());
            g.setStatus(GrantStatus.PENDING);
            g.setCreatedBy(actor);
            g.setUpdatedBy(actor);
            PositionProfileGrant saved = grants.save(g);

            // M251 / M252 — Phase F.3 + F.4: dispatch to the right
            // downstream module based on item_type. Soft-fail if anything
            // goes wrong so a single bad reference doesn't break the
            // whole hire — the grant becomes FAILED with the reason.
            saved = tryAutoFireDownstream(saved, occupancy);
            created.add(saved);
        }

        if (!created.isEmpty()) {
            audit.record(MODULE, ENTITY, occupancy.getId().toString(),
                    "AUTO_GRANT",
                    null,
                    java.util.Map.of(
                            "createdGrants", created.size(),
                            "employeeId", occupancy.getEmployeeId().toString(),
                            "positionId", occupancy.getPositionId().toString()));
        }
        return created;
    }

    // ── Operator transitions ──────────────────────────────────────────────

    /** Operator marks PENDING grant as ACTIVE — the underlying work is done. */
    @Transactional
    public PositionProfileGrant markActive(UUID grantId) {
        PositionProfileGrant g = loadOrThrow(grantId);
        if (g.getStatus() == GrantStatus.ACTIVE) return g;
        if (g.getStatus() != GrantStatus.PENDING && g.getStatus() != GrantStatus.FAILED) {
            throw new BadRequestException(
                    "Can only mark PENDING/FAILED grants as ACTIVE; current = " + g.getStatus());
        }
        // M251 / M252 — if the grant has a downstream module wired and
        // wasn't auto-fired on hire (no downstream row yet), try to fire
        // it now. Otherwise just flip the status — operator confirms the
        // work is done.
        if (g.getDownstreamEntityId() == null && hasText(g.getReferenceCode())) {
            if (g.getItemType() == ProfileItemType.ALLOWANCE) {
                return tryFireAllowanceForExistingGrant(g);
            }
            if (g.getItemType() == ProfileItemType.TRAINING) {
                return tryFireTrainingForExistingGrant(g);
            }
            if (g.getItemType() == ProfileItemType.EQUIPMENT) {
                return tryFireEquipmentForExistingGrant(g);
            }
            // M261 — Phase F.7
            if (g.getItemType() == ProfileItemType.APPROVAL_LIMIT) {
                return tryFireApprovalLimitForExistingGrant(g);
            }
            // M262 — Phase F.5a
            if (g.getItemType() == ProfileItemType.REQUIRED_DOCUMENT) {
                return tryFireRequiredDocForExistingGrant(g);
            }
            // M265 — Phase F.6
            if (g.getItemType() == ProfileItemType.ACCESS_ROLE) {
                return tryFireAccessRoleForExistingGrant(g);
            }
        }
        g.setStatus(GrantStatus.ACTIVE);
        g.setGrantedAt(OffsetDateTime.now());
        g.setGrantedBy(currentRequest.username());
        g.setUpdatedBy(currentRequest.username());
        return grants.save(g);
    }

    /**
     * Operator revokes a grant. Used both manually and via
     * {@link #revokeAllForOccupancy} when the occupancy ends.
     */
    @Transactional
    public PositionProfileGrant revoke(UUID grantId, String reason) {
        PositionProfileGrant g = loadOrThrow(grantId);
        if (g.getStatus() == GrantStatus.REVOKED) return g;
        // M251 / M253: type-aware downstream cleanup. Allowance end-dates
        // its row so payroll stops picking it up; Equipment auto-returns
        // the asset. Training intentionally leaves the enrollment alone.
        tryEndDownstreamAllowance(g);
        tryReturnDownstreamEquipment(g);
        tryEndDownstreamApprovalLimit(g);   // M261 / Phase F.7
        tryWaiveDownstreamRequiredDoc(g);   // M262 / Phase F.5a
        tryRevokeDownstreamAccessRole(g);   // M265 / Phase F.6
        g.setStatus(GrantStatus.REVOKED);
        g.setRevokedAt(OffsetDateTime.now());
        g.setRevokedBy(currentRequest.username());
        g.setRevokeReason(reason);
        g.setUpdatedBy(currentRequest.username());
        return grants.save(g);
    }

    /**
     * Bulk-revoke every non-terminal grant for an occupancy. Called by
     * {@link PositionOccupancyService#end} so ending an occupancy also
     * pulls all the associated grants.
     */
    @Transactional
    public int revokeAllForOccupancy(UUID occupancyId, String reason) {
        var rows = grants.findByOccupancyIdOrderByItemTypeAscLabelAsc(occupancyId);
        int touched = 0;
        OffsetDateTime now = OffsetDateTime.now();
        String actor = currentRequest.username();
        for (PositionProfileGrant g : rows) {
            if (g.getStatus().isTerminal()) continue;
            // M251 / M253 — type-aware downstream cleanup per row.
            // Soft-fail per row so one dangling downstream row doesn't
            // break the whole revoke.
            tryEndDownstreamAllowance(g);
            tryReturnDownstreamEquipment(g);
            tryEndDownstreamApprovalLimit(g);   // M261 / Phase F.7
            tryWaiveDownstreamRequiredDoc(g);   // M262 / Phase F.5a
            tryRevokeDownstreamAccessRole(g);   // M265 / Phase F.6
            g.setStatus(GrantStatus.REVOKED);
            g.setRevokedAt(now);
            g.setRevokedBy(actor);
            g.setRevokeReason(reason);
            g.setUpdatedBy(actor);
            grants.save(g);
            touched++;
        }
        if (touched > 0) {
            audit.record(MODULE, ENTITY, occupancyId.toString(),
                    "BULK_REVOKE",
                    null,
                    java.util.Map.of("revokedCount", touched,
                            "reason", reason == null ? "" : reason));
        }
        return touched;
    }

    /** Operator marks a grant FAILED (cross-module integration error). */
    @Transactional
    public PositionProfileGrant markFailed(UUID grantId, String reason) {
        PositionProfileGrant g = loadOrThrow(grantId);
        g.setStatus(GrantStatus.FAILED);
        g.setFailureReason(reason);
        g.setUpdatedBy(currentRequest.username());
        return grants.save(g);
    }

    private PositionProfileGrant loadOrThrow(UUID id) {
        return grants.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Grant not found: " + id));
    }

    // ── M251 / M252 — Phase F.3 + F.4 dispatcher ─────────────────────────

    /**
     * Dispatch a freshly-saved PENDING grant to its native module if it
     * carries a {@code reference_code}. Each per-type helper soft-fails
     * back to FAILED — the hire flow never throws because of a bad code.
     */
    private PositionProfileGrant tryAutoFireDownstream(PositionProfileGrant g, PositionOccupancy occ) {
        if (!hasText(g.getReferenceCode())) return g;
        switch (g.getItemType()) {
            case ALLOWANCE: return tryAutoFireAllowance(g, occ);
            case TRAINING:  return tryAutoFireTraining(g, occ);
            case EQUIPMENT: return tryAutoFireEquipment(g, occ);
            case APPROVAL_LIMIT: return tryAutoFireApprovalLimit(g, occ);
            case REQUIRED_DOCUMENT: return tryAutoFireRequiredDoc(g, occ);
            case ACCESS_ROLE: return tryAutoFireAccessRole(g);
            // Other types stay PENDING — Phase F.6+ will wire REQUIRED_DOCUMENT
            // and ACCESS_ROLE if/when the target modules exist.
            default: return g;
        }
    }

    // ── M251 — Phase F.3: ALLOWANCE wire-up ───────────────────────────────

    /**
     * Auto-fire path for grants created during {@link #autoGrantForOccupancy}.
     * Returns the grant in its final state — either ACTIVE (allowance
     * created, downstream id stashed) or FAILED (reference_code didn't
     * resolve, or downstream service threw).
     */
    private PositionProfileGrant tryAutoFireAllowance(PositionProfileGrant g, PositionOccupancy occ) {
        if (g.getValueAmount() == null) return markFailedSilently(g,
                "Allowance grant has no amount; cannot fire downstream");

        java.util.Optional<AllowanceType> typeOpt = allowanceTypes.findByCode(g.getReferenceCode());
        if (typeOpt.isEmpty()) {
            return markFailedSilently(g,
                    "No allowance_type with code '" + g.getReferenceCode() + "'");
        }
        return createDownstreamAllowance(g, typeOpt.get(), occ.getStartDate());
    }

    /**
     * Operator-triggered late-fire path called from {@link #markActive}
     * when an ALLOWANCE grant didn't auto-fire on hire (e.g. the
     * AllowanceType was added after the grant was created).
     */
    private PositionProfileGrant tryFireAllowanceForExistingGrant(PositionProfileGrant g) {
        java.util.Optional<AllowanceType> typeOpt = allowanceTypes.findByCode(g.getReferenceCode());
        if (typeOpt.isEmpty()) {
            throw new BadRequestException(
                    "No allowance_type with code '" + g.getReferenceCode() + "'");
        }
        if (g.getValueAmount() == null) {
            throw new BadRequestException("Allowance grant has no amount; cannot fire downstream");
        }
        return createDownstreamAllowance(g, typeOpt.get(), java.time.LocalDate.now());
    }

    /**
     * Build + save the EmployeeAllowanceRequest, then stash the resulting
     * row id back into the grant. Caller picks the {@code effectiveFrom}
     * (hire date for auto-fire; today for operator late-fire).
     */
    private PositionProfileGrant createDownstreamAllowance(
            PositionProfileGrant g,
            AllowanceType type,
            java.time.LocalDate effectiveFrom) {
        try {
            var req = new EmployeeAllowanceRequest(
                    g.getEmployeeId(),
                    type.getId(),
                    g.getValueAmount(),
                    hasText(g.getCurrency()) ? g.getCurrency() : type.getCurrency(),
                    effectiveFrom == null ? java.time.LocalDate.now() : effectiveFrom,
                    null,  // open-ended; ended on revoke
                    "Auto-granted from position profile (M251)");
            EmployeeAllowance created = employeeAllowanceService.create(req);

            g.setStatus(GrantStatus.ACTIVE);
            g.setGrantedAt(OffsetDateTime.now());
            g.setGrantedBy(currentRequest.username());
            g.setDownstreamEntityId(created.getId());
            g.setDownstreamEntityType("EMPLOYEE_ALLOWANCE");
            g.setUpdatedBy(currentRequest.username());
            return grants.save(g);
        } catch (RuntimeException ex) {
            return markFailedSilently(g, "Downstream allowance create failed: " + ex.getMessage());
        }
    }

    /**
     * End the linked {@code employee_allowance} row when a grant is
     * revoked. Soft-fail — log the issue on the grant but don't throw
     * so the revoke itself completes.
     */
    private void tryEndDownstreamAllowance(PositionProfileGrant g) {
        if (g.getDownstreamEntityId() == null) return;
        if (!"EMPLOYEE_ALLOWANCE".equals(g.getDownstreamEntityType())) return;
        try {
            employeeAllowanceService.end(g.getDownstreamEntityId(), java.time.LocalDate.now());
        } catch (RuntimeException ex) {
            // Stash the failure reason on the grant but don't block the revoke.
            g.setFailureReason("Downstream allowance end failed: " + ex.getMessage());
        }
    }

    // ── M252 — Phase F.4: TRAINING wire-up ────────────────────────────────

    /**
     * Auto-enrol the employee in the course identified by the grant's
     * {@code reference_code} (matched against {@code course.code}). If
     * they're already enrolled, link to the existing enrollment — that
     * way the grant is idempotent on re-hires + position re-assignments.
     *
     * <p>Soft-fail: any error → grant becomes FAILED with the reason,
     * but the rest of the hire flow continues.
     */
    private PositionProfileGrant tryAutoFireTraining(PositionProfileGrant g, PositionOccupancy occ) {
        java.util.Optional<Course> courseOpt = courses.findByCode(g.getReferenceCode());
        if (courseOpt.isEmpty()) {
            return markFailedSilently(g,
                    "No course with code '" + g.getReferenceCode() + "'");
        }
        Course course = courseOpt.get();
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            return markFailedSilently(g,
                    "Course '" + course.getCode() + "' is " + course.getStatus()
                            + " — must be PUBLISHED to auto-enrol");
        }
        return enrolDownstreamCourse(g, course);
    }

    /** Operator-triggered late-fire path called from {@link #markActive}. */
    private PositionProfileGrant tryFireTrainingForExistingGrant(PositionProfileGrant g) {
        java.util.Optional<Course> courseOpt = courses.findByCode(g.getReferenceCode());
        if (courseOpt.isEmpty()) {
            throw new BadRequestException(
                    "No course with code '" + g.getReferenceCode() + "'");
        }
        Course course = courseOpt.get();
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BadRequestException(
                    "Course '" + course.getCode() + "' is " + course.getStatus()
                            + " — must be PUBLISHED to enrol");
        }
        return enrolDownstreamCourse(g, course);
    }

    /**
     * Common enrolment path. Idempotent: if the employee already has an
     * enrollment in this course, we don't create another — we link the
     * grant to the existing one so the SPA shows the right back-link
     * and a future revoke can do the right thing.
     */
    private PositionProfileGrant enrolDownstreamCourse(PositionProfileGrant g, Course course) {
        try {
            // Check first — the underlying EnrollmentService.enroll throws
            // BadRequestException on duplicate, which we'd rather treat as
            // a soft "already done" than a failure.
            var existing = enrollments.findByCourseIdAndEmployeeId(course.getId(), g.getEmployeeId());
            UUID enrollmentId;
            if (existing.isPresent()) {
                enrollmentId = existing.get().getId();
            } else {
                Enrollment created = enrollmentService.enroll(
                        new EnrollRequest(course.getId(), g.getEmployeeId(),
                                EnrolledVia.ASSIGNED, null));
                enrollmentId = created.getId();
            }

            g.setStatus(GrantStatus.ACTIVE);
            g.setGrantedAt(OffsetDateTime.now());
            g.setGrantedBy(currentRequest.username());
            g.setDownstreamEntityId(enrollmentId);
            g.setDownstreamEntityType("ENROLLMENT");
            g.setUpdatedBy(currentRequest.username());
            return grants.save(g);
        } catch (RuntimeException ex) {
            return markFailedSilently(g, "Downstream enrollment create failed: " + ex.getMessage());
        }
    }

    // Note: TRAINING grants are *not* withdrawn on revoke. A completed
    // training stays valid historically even after the employee changes
    // position — there's no clean way to "un-train" someone. So
    // revoke()/revokeAllForOccupancy just flip the grant status and
    // leave the enrollment in place.

    // ── M253 — Phase F.5b: EQUIPMENT wire-up ──────────────────────────────

    /**
     * Auto-issue a company asset to the employee on hire. The grant's
     * {@code reference_code} is parsed as an {@link AssetType} enum value
     * (LAPTOP, MOBILE_PHONE, VEHICLE, UNIFORM, …); anything that doesn't
     * match falls back to {@link AssetType#EQUIPMENT}. Asset name comes
     * from the grant's label so HR can be specific ("MacBook Pro 16",
     * "Toyota Hilux fleet veh.").
     */
    private PositionProfileGrant tryAutoFireEquipment(PositionProfileGrant g, PositionOccupancy occ) {
        AssetType type = resolveAssetType(g.getReferenceCode());
        return assignDownstreamEquipment(g, type, occ.getStartDate());
    }

    private PositionProfileGrant tryFireEquipmentForExistingGrant(PositionProfileGrant g) {
        AssetType type = resolveAssetType(g.getReferenceCode());
        return assignDownstreamEquipment(g, type, java.time.LocalDate.now());
    }

    /**
     * Common assignment path. Note: unlike allowances + training, we
     * don't pre-check for an existing assignment — every fresh hire
     * legitimately gets a fresh piece of equipment, so a duplicate is
     * expected behavior on re-hire.
     */
    private PositionProfileGrant assignDownstreamEquipment(
            PositionProfileGrant g, AssetType type, java.time.LocalDate assignedAt) {
        try {
            AssetRequest req = new AssetRequest(
                    type,
                    g.getLabel(),
                    null,                            // identifier filled in by HR later
                    null,                            // description
                    assignedAt == null ? java.time.LocalDate.now() : assignedAt,
                    null,                            // expectedReturnDate
                    null,                            // conditionAtAssignment
                    null,                            // custodyFormUrl
                    "Auto-granted from position profile (M253)");
            AssetResponse created = employeeAssetService.assign(g.getEmployeeId(), req);

            g.setStatus(GrantStatus.ACTIVE);
            g.setGrantedAt(OffsetDateTime.now());
            g.setGrantedBy(currentRequest.username());
            g.setDownstreamEntityId(created.id());
            g.setDownstreamEntityType("EMPLOYEE_ASSET");
            g.setUpdatedBy(currentRequest.username());
            return grants.save(g);
        } catch (RuntimeException ex) {
            return markFailedSilently(g, "Downstream asset assign failed: " + ex.getMessage());
        }
    }

    /**
     * Auto-return the linked {@code employee_asset} row when the grant is
     * revoked. Soft-fail — the asset may already be in a terminal state
     * (LOST / DAMAGED / WRITTEN_OFF) which the state machine would block.
     */
    private void tryReturnDownstreamEquipment(PositionProfileGrant g) {
        if (g.getDownstreamEntityId() == null) return;
        if (!"EMPLOYEE_ASSET".equals(g.getDownstreamEntityType())) return;
        try {
            employeeAssetService.close(g.getDownstreamEntityId(),
                    new AssetReturnRequest(
                            AssetStatus.RETURNED,
                            java.time.LocalDate.now(),
                            null,
                            "Auto-returned on occupancy end (M253)"));
        } catch (RuntimeException ex) {
            // Don't block the revoke — record the issue on the grant.
            g.setFailureReason("Downstream asset return failed: " + ex.getMessage());
        }
    }

    /**
     * Parse the reference_code as an AssetType. Falls back to the
     * generic {@link AssetType#EQUIPMENT} so a position can carry items
     * like "Forklift" without forcing a new enum value.
     */
    private AssetType resolveAssetType(String code) {
        if (!hasText(code)) return AssetType.EQUIPMENT;
        try {
            return AssetType.valueOf(code.toUpperCase().trim());
        } catch (IllegalArgumentException ex) {
            return AssetType.EQUIPMENT;
        }
    }

    // ── M261 / Phase F.7 — APPROVAL_LIMIT wire-up ─────────────────────

    /**
     * Auto-create an employee_approval_limit row when an APPROVAL_LIMIT
     * grant fires for a new occupancy.
     *
     * <p>{@code reference_code} is parsed as an {@link ApprovalLimitType}
     * enum value (PURCHASE_ORDER, EXPENSE_REPORT, …); anything that
     * doesn't match falls back to {@link ApprovalLimitType#GENERAL}.
     * Amount + currency come from the grant's snapshot.
     *
     * <p>Soft-fail — if value_amount is null we can't create the
     * downstream row (no meaningful limit), so we mark the grant
     * FAILED with a clear reason rather than throwing.
     */
    private PositionProfileGrant tryAutoFireApprovalLimit(PositionProfileGrant g, PositionOccupancy occ) {
        ApprovalLimitType type = resolveApprovalLimitType(g.getReferenceCode());
        return assignDownstreamApprovalLimit(g, type, occ.getStartDate());
    }

    private PositionProfileGrant tryFireApprovalLimitForExistingGrant(PositionProfileGrant g) {
        ApprovalLimitType type = resolveApprovalLimitType(g.getReferenceCode());
        return assignDownstreamApprovalLimit(g, type, java.time.LocalDate.now());
    }

    private PositionProfileGrant assignDownstreamApprovalLimit(
            PositionProfileGrant g, ApprovalLimitType type, java.time.LocalDate effectiveFrom) {
        if (g.getValueAmount() == null) {
            return markFailedSilently(g,
                    "APPROVAL_LIMIT grant has no value_amount — set a max amount on the profile item.");
        }
        try {
            EmployeeApprovalLimit created = approvalLimitService.assign(
                    g.getEmployeeId(),
                    type,
                    g.getValueAmount(),
                    g.getCurrency(),
                    effectiveFrom == null ? java.time.LocalDate.now() : effectiveFrom,
                    "PROFILE_GRANT",
                    g.getId(),
                    "Auto-granted from position profile (M261)");

            g.setStatus(GrantStatus.ACTIVE);
            g.setGrantedAt(OffsetDateTime.now());
            g.setGrantedBy(currentRequest.username());
            g.setDownstreamEntityId(created.getId());
            g.setDownstreamEntityType("EMPLOYEE_APPROVAL_LIMIT");
            g.setUpdatedBy(currentRequest.username());
            return grants.save(g);
        } catch (RuntimeException ex) {
            return markFailedSilently(g, "Downstream approval-limit assign failed: " + ex.getMessage());
        }
    }

    /**
     * Effective-date the linked approval_limit row when the grant is
     * revoked. Soft-fail — if the limit was already ended manually,
     * the service returns the row unchanged.
     */
    private void tryEndDownstreamApprovalLimit(PositionProfileGrant g) {
        if (g.getDownstreamEntityId() == null) return;
        if (!"EMPLOYEE_APPROVAL_LIMIT".equals(g.getDownstreamEntityType())) return;
        try {
            approvalLimitService.end(g.getDownstreamEntityId(),
                    java.time.LocalDate.now(),
                    "Auto-ended on grant revoke (M261)");
        } catch (RuntimeException ex) {
            g.setFailureReason("Downstream approval-limit end failed: " + ex.getMessage());
        }
    }

    private ApprovalLimitType resolveApprovalLimitType(String code) {
        if (!hasText(code)) return ApprovalLimitType.GENERAL;
        try {
            return ApprovalLimitType.valueOf(code.toUpperCase().trim());
        } catch (IllegalArgumentException ex) {
            return ApprovalLimitType.GENERAL;
        }
    }

    // ── M262 / Phase F.5a — REQUIRED_DOCUMENT wire-up ────────────────

    /**
     * Auto-create a required_employee_document row when a REQUIRED_DOCUMENT
     * grant fires for a new occupancy.
     *
     * <p>{@code reference_code} is parsed as a {@link DocumentRequirementType}
     * enum value (PASSPORT, DIPLOMA, NDA, …); anything that doesn't match
     * falls back to {@link DocumentRequirementType#OTHER}. The grant's
     * label becomes the human-readable document name.
     */
    private PositionProfileGrant tryAutoFireRequiredDoc(PositionProfileGrant g, PositionOccupancy occ) {
        DocumentRequirementType type = resolveDocType(g.getReferenceCode());
        return assignDownstreamRequiredDoc(g, type);
    }

    private PositionProfileGrant tryFireRequiredDocForExistingGrant(PositionProfileGrant g) {
        DocumentRequirementType type = resolveDocType(g.getReferenceCode());
        return assignDownstreamRequiredDoc(g, type);
    }

    private PositionProfileGrant assignDownstreamRequiredDoc(
            PositionProfileGrant g, DocumentRequirementType type) {
        try {
            RequiredEmployeeDocument created = requiredDocService.assign(
                    g.getEmployeeId(),
                    type,
                    g.getLabel(),
                    null,                  // required_by_date — operator sets later if needed
                    "PROFILE_GRANT",
                    g.getId(),
                    "Auto-required from position profile (M262)");

            g.setStatus(GrantStatus.ACTIVE);
            g.setGrantedAt(OffsetDateTime.now());
            g.setGrantedBy(currentRequest.username());
            g.setDownstreamEntityId(created.getId());
            g.setDownstreamEntityType("REQUIRED_EMPLOYEE_DOCUMENT");
            g.setUpdatedBy(currentRequest.username());
            return grants.save(g);
        } catch (RuntimeException ex) {
            return markFailedSilently(g, "Downstream required-document assign failed: " + ex.getMessage());
        }
    }

    /**
     * Waive the linked requirement row when the grant is revoked. Soft-fail —
     * if the doc was already SATISFIED or WAIVED, the service returns the
     * row unchanged (no-op in those cases).
     */
    private void tryWaiveDownstreamRequiredDoc(PositionProfileGrant g) {
        if (g.getDownstreamEntityId() == null) return;
        if (!"REQUIRED_EMPLOYEE_DOCUMENT".equals(g.getDownstreamEntityType())) return;
        try {
            requiredDocService.waive(g.getDownstreamEntityId(),
                    "Auto-waived on grant revoke (M262)");
        } catch (RuntimeException ex) {
            g.setFailureReason("Downstream required-document waive failed: " + ex.getMessage());
        }
    }

    private DocumentRequirementType resolveDocType(String code) {
        if (!hasText(code)) return DocumentRequirementType.OTHER;
        try {
            return DocumentRequirementType.valueOf(code.toUpperCase().trim());
        } catch (IllegalArgumentException ex) {
            return DocumentRequirementType.OTHER;
        }
    }

    // ── M265 / Phase F.6 — ACCESS_ROLE → Keycloak realm role ────────

    /**
     * Auto-grant a Keycloak realm role when an ACCESS_ROLE grant fires.
     *
     * <p>{@code reference_code} is treated as the realm role name (e.g.
     * "HR_SPECIALIST"). We look up the Keycloak user_id from the
     * employee's username, then grant the role idempotently.
     *
     * <p>Soft-fail when the employee has no username yet OR no matching
     * Keycloak user — the grant ends up FAILED with a clear reason so HR
     * can re-fire it via markActive() after provisioning the SSO account.
     */
    private PositionProfileGrant tryAutoFireAccessRole(PositionProfileGrant g) {
        return assignDownstreamAccessRole(g);
    }

    private PositionProfileGrant tryFireAccessRoleForExistingGrant(PositionProfileGrant g) {
        return assignDownstreamAccessRole(g);
    }

    private PositionProfileGrant assignDownstreamAccessRole(PositionProfileGrant g) {
        if (!hasText(g.getReferenceCode())) {
            return markFailedSilently(g,
                    "ACCESS_ROLE grant has no reference_code — set the realm role name on the profile item.");
        }
        var emp = employees.findById(g.getEmployeeId()).orElse(null);
        if (emp == null) {
            return markFailedSilently(g, "Employee not found for ACCESS_ROLE grant");
        }
        if (!hasText(emp.getUsername())) {
            return markFailedSilently(g,
                    "Employee has no username yet — provision SSO account, then mark active.");
        }
        try {
            var userIdOpt = keycloakAdminService.findUserIdByUsername(emp.getUsername());
            if (userIdOpt.isEmpty()) {
                return markFailedSilently(g,
                        "No Keycloak user for '" + emp.getUsername() + "' — provision the account first.");
            }
            String userId = userIdOpt.get();
            keycloakAdminService.addRealmRoleToUser(userId, g.getReferenceCode());

            g.setStatus(GrantStatus.ACTIVE);
            g.setGrantedAt(OffsetDateTime.now());
            g.setGrantedBy(currentRequest.username());
            // No DB row downstream — store the realm role name as a synthetic
            // identifier so revoke can look it up without re-reading the grant
            // (Keycloak doesn't give us its own row id for the mapping itself).
            g.setDownstreamEntityType("KEYCLOAK_REALM_ROLE");
            g.setUpdatedBy(currentRequest.username());
            return grants.save(g);
        } catch (RuntimeException ex) {
            return markFailedSilently(g, "Keycloak role grant failed: " + ex.getMessage());
        }
    }

    /**
     * Revoke the realm role on grant-revoke. Soft-fail — if Keycloak is down,
     * the grant still flips to REVOKED locally so the operator's intent is
     * recorded; the role can be cleaned up out-of-band.
     */
    private void tryRevokeDownstreamAccessRole(PositionProfileGrant g) {
        if (!"KEYCLOAK_REALM_ROLE".equals(g.getDownstreamEntityType())) return;
        if (!hasText(g.getReferenceCode())) return;
        try {
            var emp = employees.findById(g.getEmployeeId()).orElse(null);
            if (emp == null || !hasText(emp.getUsername())) return;
            var userIdOpt = keycloakAdminService.findUserIdByUsername(emp.getUsername());
            if (userIdOpt.isEmpty()) return;
            keycloakAdminService.removeRealmRoleFromUser(userIdOpt.get(), g.getReferenceCode());
        } catch (RuntimeException ex) {
            g.setFailureReason("Keycloak role revoke failed: " + ex.getMessage());
        }
    }

    private PositionProfileGrant markFailedSilently(PositionProfileGrant g, String reason) {
        g.setStatus(GrantStatus.FAILED);
        g.setFailureReason(reason);
        g.setUpdatedBy(currentRequest.username());
        return grants.save(g);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
