package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.api.dto.OkrDtos.CheckInRequest;
import az.millers.hcm.performance.api.dto.OkrDtos.CheckInResponse;
import az.millers.hcm.performance.api.dto.OkrDtos.KeyResultRequest;
import az.millers.hcm.performance.api.dto.OkrDtos.KeyResultResponse;
import az.millers.hcm.performance.api.dto.OkrDtos.ObjectiveRequest;
import az.millers.hcm.performance.api.dto.OkrDtos.ObjectiveResponse;
import az.millers.hcm.performance.domain.OkrCheckIn;
import az.millers.hcm.performance.domain.OkrKeyResult;
import az.millers.hcm.performance.domain.OkrObjective;
import az.millers.hcm.performance.repo.OkrCheckInRepository;
import az.millers.hcm.performance.repo.OkrKeyResultRepository;
import az.millers.hcm.performance.repo.OkrObjectiveRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * HCM_12 M391 — OKR objectives (6 levels, parent alignment), key results, and
 * check-ins with automatic progress roll-up (see {@link OkrMath}).
 */
@Service
public class OkrService {

    private static final String TENANT = "default";
    private static final String MODULE = "PERFORMANCE";
    private static final Set<String> LEVELS =
            Set.of("COMPANY", "LEGAL_ENTITY", "BUSINESS_UNIT", "DEPARTMENT", "TEAM", "INDIVIDUAL");
    private static final Set<String> CONFIDENCES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> KR_STATUSES = Set.of("ACTIVE", "DONE", "AT_RISK", "CANCELLED");

    private final OkrObjectiveRepository objectives;
    private final OkrKeyResultRepository keyResults;
    private final OkrCheckInRepository checkIns;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final az.millers.hcm.security.scope.AccessScopeService accessScope;

    public OkrService(OkrObjectiveRepository objectives,
                      OkrKeyResultRepository keyResults,
                      OkrCheckInRepository checkIns,
                      EmployeeRepository employees,
                      AuditService audit,
                      CurrentRequest currentRequest,
                      az.millers.hcm.security.scope.AccessScopeService accessScope) {
        this.objectives = objectives;
        this.keyResults = keyResults;
        this.checkIns = checkIns;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.accessScope = accessScope;
    }

    /**
     * GLOBAL RULES 7/8 (M402 security-gate fix) — org-level objectives
     * (COMPANY…TEAM, no owner) are visible to every reader; INDIVIDUAL
     * objectives only within the caller's hierarchy scope.
     */
    private boolean visible(OkrObjective o) {
        return o.getOwnerEmployeeId() == null || accessScope.isAccessible(o.getOwnerEmployeeId());
    }

    // ── Objectives ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ObjectiveResponse> list(UUID cycleId, String level, UUID ownerEmployeeId) {
        List<OkrObjective> rows;
        if (ownerEmployeeId != null) {
            rows = objectives.findByTenantIdAndOwnerEmployeeIdOrderByCreatedAtDesc(TENANT, ownerEmployeeId);
        } else if (cycleId != null && level != null) {
            rows = objectives.findByTenantIdAndCycleIdAndOkrLevelOrderByCreatedAtDesc(TENANT, cycleId, level);
        } else if (cycleId != null) {
            rows = objectives.findByTenantIdAndCycleIdOrderByCreatedAtDesc(TENANT, cycleId);
        } else if (level != null) {
            rows = objectives.findByTenantIdAndOkrLevelOrderByCreatedAtDesc(TENANT, level);
        } else {
            rows = objectives.findByTenantIdOrderByCreatedAtDesc(TENANT);
        }
        return decorate(rows.stream().filter(this::visible).toList());
    }

    @Transactional(readOnly = true)
    public ObjectiveResponse get(UUID id) {
        OkrObjective o = require(id);
        if (!visible(o)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Objective is outside your access scope");
        }
        return decorate(List.of(o)).get(0);
    }

    @Transactional
    public ObjectiveResponse create(ObjectiveRequest req) {
        validate(req);
        OkrObjective o = new OkrObjective();
        o.setTenantId(TENANT);
        apply(o, req);
        o.setStatus("ACTIVE");
        o.setCreatedBy(currentRequest.username());
        OkrObjective saved = objectives.save(o);
        audit.record(MODULE, "OkrObjective", saved.getId().toString(), "CREATE",
                null, req.okrLevel() + " — " + req.title());
        return decorate(List.of(saved)).get(0);
    }

    @Transactional
    public ObjectiveResponse update(UUID id, ObjectiveRequest req) {
        validate(req);
        OkrObjective o = require(id);
        if (req.parentId() != null && req.parentId().equals(id)) {
            throw new BadRequestException("An objective cannot be its own parent");
        }
        String before = o.getOkrLevel() + " — " + o.getTitle();
        apply(o, req);
        OkrObjective saved = objectives.save(o);
        audit.record(MODULE, "OkrObjective", id.toString(), "UPDATE",
                before, req.okrLevel() + " — " + req.title());
        return decorate(List.of(saved)).get(0);
    }

    @Transactional
    public ObjectiveResponse changeStatus(UUID id, String status) {
        if (!Set.of("DRAFT", "ACTIVE", "CLOSED", "CANCELLED").contains(status)) {
            throw new BadRequestException("Unknown objective status: " + status);
        }
        OkrObjective o = require(id);
        String before = o.getStatus();
        o.setStatus(status);
        OkrObjective saved = objectives.save(o);
        audit.record(MODULE, "OkrObjective", id.toString(), "STATUS", before, status);
        return decorate(List.of(saved)).get(0);
    }

    private void validate(ObjectiveRequest req) {
        if (!LEVELS.contains(req.okrLevel())) {
            throw new BadRequestException("Unknown OKR level: " + req.okrLevel());
        }
        if ("INDIVIDUAL".equals(req.okrLevel()) && req.ownerEmployeeId() == null) {
            throw new BadRequestException("An INDIVIDUAL objective requires an owner employee");
        }
        if (req.ownerEmployeeId() != null && !employees.existsById(req.ownerEmployeeId())) {
            throw new BadRequestException("Employee not found: " + req.ownerEmployeeId());
        }
        if (req.confidence() != null && !CONFIDENCES.contains(req.confidence())) {
            throw new BadRequestException("Confidence must be HIGH, MEDIUM or LOW");
        }
        if (req.parentId() != null && !objectives.existsById(req.parentId())) {
            throw new BadRequestException("Parent objective not found: " + req.parentId());
        }
    }

    private void apply(OkrObjective o, ObjectiveRequest req) {
        o.setTitle(req.title());
        o.setDescription(req.description());
        o.setOkrLevel(req.okrLevel());
        o.setParentId(req.parentId());
        o.setOwnerEmployeeId(req.ownerEmployeeId());
        o.setOrgUnitId(req.orgUnitId());
        o.setLegalEntityId(req.legalEntityId());
        o.setCycleId(req.cycleId());
        o.setPeriodStart(req.periodStart());
        o.setPeriodEnd(req.periodEnd());
        o.setDueDate(req.dueDate());
        o.setConfidence(req.confidence());
    }

    private OkrObjective require(UUID id) {
        return objectives.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Objective not found: " + id));
    }

    private List<ObjectiveResponse> decorate(List<OkrObjective> rows) {
        Map<UUID, String> titleCache = new HashMap<>();
        rows.forEach(o -> titleCache.put(o.getId(), o.getTitle()));
        Map<UUID, String> nameCache = new HashMap<>();
        Map<UUID, List<OkrKeyResult>> krByObjective = new HashMap<>();
        if (!rows.isEmpty()) {
            keyResults.findByObjectiveIdInOrderByCreatedAtAsc(rows.stream().map(OkrObjective::getId).toList())
                    .forEach(kr -> krByObjective.computeIfAbsent(kr.getObjectiveId(), k -> new java.util.ArrayList<>()).add(kr));
        }
        return rows.stream().map(o -> {
            String parentTitle = o.getParentId() == null ? null
                    : titleCache.computeIfAbsent(o.getParentId(),
                            id -> objectives.findById(id).map(OkrObjective::getTitle).orElse(null));
            String ownerName = o.getOwnerEmployeeId() == null ? null
                    : nameCache.computeIfAbsent(o.getOwnerEmployeeId(),
                            id -> employees.findById(id)
                                    .map(e -> (e.getFirstName() + " " + e.getLastName()).trim()).orElse(null));
            return ObjectiveResponse.from(o, parentTitle, ownerName,
                    krByObjective.getOrDefault(o.getId(), List.of()));
        }).toList();
    }

    // ── Key results ──────────────────────────────────────────────────────────

    @Transactional
    public KeyResultResponse addKeyResult(UUID objectiveId, KeyResultRequest req) {
        OkrObjective o = require(objectiveId);
        assertObjectiveOpen(o);
        OkrKeyResult kr = new OkrKeyResult();
        kr.setTenantId(o.getTenantId());
        kr.setObjectiveId(objectiveId);
        applyKr(kr, req);
        OkrKeyResult saved = keyResults.save(kr);
        rollUp(o);
        audit.record(MODULE, "OkrKeyResult", saved.getId().toString(), "CREATE",
                null, req.title());
        return KeyResultResponse.from(saved);
    }

    @Transactional
    public KeyResultResponse updateKeyResult(UUID krId, KeyResultRequest req) {
        OkrKeyResult kr = requireKr(krId);
        OkrObjective o = require(kr.getObjectiveId());
        assertObjectiveOpen(o);
        String before = kr.getTitle();
        applyKr(kr, req);
        // targets/baseline may have moved — recompute from the existing current value
        kr.setProgressPercent(OkrMath.krProgress(kr.getMeasurementType(), kr.getBaselineValue(),
                kr.getTargetValue(), kr.getCurrentValue()));
        OkrKeyResult saved = keyResults.save(kr);
        rollUp(o);
        audit.record(MODULE, "OkrKeyResult", krId.toString(), "UPDATE", before, req.title());
        return KeyResultResponse.from(saved);
    }

    private void applyKr(OkrKeyResult kr, KeyResultRequest req) {
        if (req.measurementType() != null
                && !Set.of("NUMBER", "PERCENT", "CURRENCY", "BOOLEAN").contains(req.measurementType())) {
            throw new BadRequestException("Unknown measurement type: " + req.measurementType());
        }
        if (req.confidence() != null && !CONFIDENCES.contains(req.confidence())) {
            throw new BadRequestException("Confidence must be HIGH, MEDIUM or LOW");
        }
        kr.setTitle(req.title());
        if (req.measurementType() != null) kr.setMeasurementType(req.measurementType());
        kr.setBaselineValue(req.baselineValue() == null ? BigDecimal.ZERO : req.baselineValue());
        kr.setTargetValue(req.targetValue());
        kr.setWeightPercent(req.weightPercent() == null ? BigDecimal.ZERO : req.weightPercent());
        kr.setConfidence(req.confidence());
        kr.setOwnerEmployeeId(req.ownerEmployeeId());
        kr.setDueDate(req.dueDate());
    }

    // ── Check-ins ────────────────────────────────────────────────────────────

    /**
     * Records a check-in. With a keyResultId + currentValue it updates the KR and
     * recomputes progress + objective roll-up; without, it is an objective-level
     * comment / confidence update (§8.1).
     */
    @Transactional
    public ObjectiveResponse checkIn(UUID objectiveId, CheckInRequest req) {
        OkrObjective o = require(objectiveId);
        assertObjectiveOpen(o);
        if (req.confidence() != null && !CONFIDENCES.contains(req.confidence())) {
            throw new BadRequestException("Confidence must be HIGH, MEDIUM or LOW");
        }

        OkrCheckIn ci = new OkrCheckIn();
        ci.setTenantId(o.getTenantId());
        ci.setObjectiveId(objectiveId);
        ci.setConfidence(req.confidence());
        ci.setComment(req.comment());
        ci.setRecordedBy(currentRequest.username());

        if (req.keyResultId() != null) {
            OkrKeyResult kr = requireKr(req.keyResultId());
            if (!objectiveId.equals(kr.getObjectiveId())) {
                throw new BadRequestException("Key result does not belong to this objective");
            }
            if ("CANCELLED".equals(kr.getStatus())) {
                throw new BadRequestException("Key result is cancelled");
            }
            ci.setKeyResultId(kr.getId());
            ci.setOldValue(kr.getCurrentValue());
            if (req.currentValue() != null) {
                kr.setCurrentValue(req.currentValue());
                kr.setProgressPercent(OkrMath.krProgress(kr.getMeasurementType(),
                        kr.getBaselineValue(), kr.getTargetValue(), req.currentValue()));
                ci.setNewValue(req.currentValue());
            }
            if (req.confidence() != null) kr.setConfidence(req.confidence());
            if (req.status() != null) {
                if (!KR_STATUSES.contains(req.status())) {
                    throw new BadRequestException("Unknown key-result status: " + req.status());
                }
                kr.setStatus(req.status());
            }
            keyResults.save(kr);
        } else if (req.confidence() != null) {
            o.setConfidence(req.confidence());
        }

        checkIns.save(ci);
        rollUp(o);
        objectives.save(o);
        audit.record(MODULE, "OkrObjective", objectiveId.toString(), "CHECK_IN",
                null, Map.of("keyResultId", String.valueOf(req.keyResultId()),
                        "value", String.valueOf(req.currentValue()),
                        "progress", String.valueOf(o.getProgressPercent())));
        return decorate(List.of(o)).get(0);
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> checkInHistory(UUID objectiveId) {
        return checkIns.findByObjectiveIdOrderByRecordedAtDesc(objectiveId).stream()
                .map(CheckInResponse::from).toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Objective progress = weighted average of non-cancelled KR progress. */
    private void rollUp(OkrObjective o) {
        List<BigDecimal[]> items = keyResults.findByObjectiveIdOrderByCreatedAtAsc(o.getId()).stream()
                .filter(kr -> !"CANCELLED".equals(kr.getStatus()))
                .map(kr -> new BigDecimal[] { kr.getProgressPercent(), kr.getWeightPercent() })
                .toList();
        o.setProgressPercent(OkrMath.objectiveProgress(items));
        objectives.save(o);
    }

    private void assertObjectiveOpen(OkrObjective o) {
        if ("CLOSED".equals(o.getStatus()) || "CANCELLED".equals(o.getStatus())) {
            throw new BadRequestException("Objective is " + o.getStatus().toLowerCase());
        }
    }

    private OkrKeyResult requireKr(UUID id) {
        return keyResults.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Key result not found: " + id));
    }
}
