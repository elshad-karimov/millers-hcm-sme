package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.api.dto.KpiDtos.AssignRequest;
import az.millers.hcm.performance.api.dto.KpiDtos.AssignmentResponse;
import az.millers.hcm.performance.api.dto.KpiDtos.KpiRequest;
import az.millers.hcm.performance.api.dto.KpiDtos.KpiResponse;
import az.millers.hcm.performance.api.dto.KpiDtos.MeasureRequest;
import az.millers.hcm.performance.api.dto.KpiDtos.ResultResponse;
import az.millers.hcm.performance.domain.Kpi;
import az.millers.hcm.performance.domain.KpiAssignment;
import az.millers.hcm.performance.domain.KpiResult;
import az.millers.hcm.performance.repo.KpiAssignmentRepository;
import az.millers.hcm.performance.repo.KpiRepository;
import az.millers.hcm.performance.repo.KpiResultRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * HCM_12 M390 — KPI library CRUD, assignment to employee+cycle, and measurement
 * recording with §7.4 scoring (see {@link KpiScoringMath}).
 */
@Service
public class KpiService {

    private static final String TENANT = "default";
    private static final String MODULE = "PERFORMANCE";

    private final KpiRepository kpis;
    private final KpiAssignmentRepository assignments;
    private final KpiResultRepository results;
    private final EmployeeRepository employees;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public KpiService(KpiRepository kpis,
                      KpiAssignmentRepository assignments,
                      KpiResultRepository results,
                      EmployeeRepository employees,
                      AccessScopeService accessScope,
                      AuditService audit,
                      CurrentRequest currentRequest) {
        this.kpis = kpis;
        this.assignments = assignments;
        this.results = results;
        this.employees = employees;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Library ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<KpiResponse> listKpis(boolean activeOnly) {
        List<Kpi> rows = activeOnly
                ? kpis.findByTenantIdAndActiveTrueOrderByKpiNameAsc(TENANT)
                : kpis.findByTenantIdOrderByKpiNameAsc(TENANT);
        return rows.stream().map(KpiResponse::from).toList();
    }

    @Transactional
    public KpiResponse createKpi(KpiRequest req) {
        String code = req.kpiCode().trim().toUpperCase();
        if (kpis.existsByTenantIdAndKpiCode(TENANT, code)) {
            throw new BadRequestException("KPI code already exists: " + code);
        }
        Kpi k = new Kpi();
        k.setTenantId(TENANT);
        k.setKpiCode(code);
        applyKpi(k, req);
        k.setCreatedBy(currentRequest.username());
        Kpi saved = kpis.save(k);
        KpiResponse response = KpiResponse.from(saved);
        audit.record(MODULE, "Kpi", saved.getId().toString(), "CREATE", null, response);
        return response;
    }

    @Transactional
    public KpiResponse updateKpi(UUID id, KpiRequest req) {
        Kpi k = kpis.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KPI not found: " + id));
        KpiResponse before = KpiResponse.from(k);
        String code = req.kpiCode().trim().toUpperCase();
        if (!k.getKpiCode().equals(code) && kpis.existsByTenantIdAndKpiCode(TENANT, code)) {
            throw new BadRequestException("KPI code already exists: " + code);
        }
        k.setKpiCode(code);
        applyKpi(k, req);
        Kpi saved = kpis.save(k);
        KpiResponse response = KpiResponse.from(saved);
        audit.record(MODULE, "Kpi", id.toString(), "UPDATE", before, response);
        return response;
    }

    private void applyKpi(Kpi k, KpiRequest req) {
        k.setKpiName(req.kpiName());
        k.setCategory(req.category());
        k.setDescription(req.description());
        k.setMeasurementUnit(req.measurementUnit());
        k.setDefaultTarget(req.defaultTarget());
        k.setMinThreshold(req.minThreshold());
        k.setMaxThreshold(req.maxThreshold());
        k.setFrequency(req.frequency() == null ? "ANNUAL" : req.frequency());
        k.setScoringModel(req.scoringModel() == null ? "LINEAR" : req.scoringModel());
        k.setDataSource(req.dataSource() == null ? "MANUAL" : req.dataSource());
        k.setActive(req.active() == null ? true : req.active());
    }

    // ── Assignments ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AssignmentResponse> listAssignments(UUID cycleId, UUID employeeId) {
        if (employeeId != null && !accessScope.isAccessible(employeeId)) {
            return List.of();
        }
        List<KpiAssignment> rows;
        if (cycleId != null && employeeId != null) {
            rows = assignments.findByTenantIdAndCycleIdAndEmployeeIdOrderByCreatedAtAsc(TENANT, cycleId, employeeId);
        } else if (cycleId != null) {
            rows = assignments.findByTenantIdAndCycleIdOrderByCreatedAtAsc(TENANT, cycleId);
        } else if (employeeId != null) {
            rows = assignments.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TENANT, employeeId);
        } else {
            throw new BadRequestException("cycleId or employeeId is required");
        }
        return decorate(rows);
    }

    private List<AssignmentResponse> decorate(List<KpiAssignment> rows) {
        Map<UUID, Kpi> kpiCache = new HashMap<>();
        Map<UUID, String> nameCache = new HashMap<>();
        return rows.stream().map(a -> AssignmentResponse.from(a,
                kpiCache.computeIfAbsent(a.getKpiId(), id -> kpis.findById(id).orElse(null)),
                nameCache.computeIfAbsent(a.getEmployeeId(), id -> employees.findById(id)
                        .map(e -> (e.getFirstName() + " " + e.getLastName()).trim()).orElse(null))))
                .toList();
    }

    @Transactional
    public AssignmentResponse assign(AssignRequest req) {
        Kpi kpi = kpis.findById(req.kpiId())
                .orElseThrow(() -> new ResourceNotFoundException("KPI not found: " + req.kpiId()));
        if (!kpi.isActive()) {
            throw new BadRequestException("KPI is not active: " + kpi.getKpiCode());
        }
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        if (assignments.existsByKpiIdAndCycleIdAndEmployeeId(req.kpiId(), req.cycleId(), req.employeeId())) {
            throw new BadRequestException("This KPI is already assigned to the employee for this cycle");
        }
        KpiAssignment a = new KpiAssignment();
        a.setTenantId(TENANT);
        a.setKpiId(req.kpiId());
        a.setCycleId(req.cycleId());
        a.setEmployeeId(req.employeeId());
        a.setAssignedTarget(req.assignedTarget());
        a.setWeightPercent(req.weightPercent() == null ? BigDecimal.ZERO : req.weightPercent());
        a.setAssignedBy(currentRequest.username());
        KpiAssignment saved = assignments.save(a);
        audit.record(MODULE, "KpiAssignment", saved.getId().toString(), "ASSIGN",
                null, kpi.getKpiCode() + " → " + req.employeeId());
        return decorate(List.of(saved)).get(0);
    }

    /** Record a measurement: computes achievement % + rating (§7.4) and appends history. */
    @Transactional
    public AssignmentResponse measure(UUID assignmentId, MeasureRequest req) {
        KpiAssignment a = assignments.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("KPI assignment not found: " + assignmentId));
        if ("CANCELLED".equals(a.getStatus())) {
            throw new BadRequestException("Assignment is cancelled");
        }
        Kpi kpi = kpis.findById(a.getKpiId())
                .orElseThrow(() -> new ResourceNotFoundException("KPI not found: " + a.getKpiId()));

        BigDecimal achievement = KpiScoringMath.achievementPercent(req.actualValue(), a.getAssignedTarget());
        BigDecimal rating = KpiScoringMath.rating(kpi.getScoringModel(), achievement);

        a.setActualValue(req.actualValue());
        a.setAchievementPercent(achievement);
        a.setRating(rating);
        a.setStatus("MEASURED");
        KpiAssignment saved = assignments.save(a);

        KpiResult r = new KpiResult();
        r.setTenantId(TENANT);
        r.setAssignmentId(assignmentId);
        r.setPeriodLabel(req.periodLabel());
        r.setActualValue(req.actualValue());
        r.setAchievementPercent(achievement);
        r.setRating(rating);
        r.setNote(req.note());
        r.setRecordedBy(currentRequest.username());
        results.save(r);

        audit.record(MODULE, "KpiAssignment", assignmentId.toString(), "MEASURE",
                null, Map.of("actual", req.actualValue(), "achievement", String.valueOf(achievement),
                        "rating", String.valueOf(rating)));
        return decorate(List.of(saved)).get(0);
    }

    @Transactional
    public AssignmentResponse cancelAssignment(UUID assignmentId) {
        KpiAssignment a = assignments.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("KPI assignment not found: " + assignmentId));
        a.setStatus("CANCELLED");
        KpiAssignment saved = assignments.save(a);
        audit.record(MODULE, "KpiAssignment", assignmentId.toString(), "CANCEL", null, "CANCELLED");
        return decorate(List.of(saved)).get(0);
    }

    @Transactional(readOnly = true)
    public List<ResultResponse> history(UUID assignmentId) {
        return results.findByAssignmentIdOrderByRecordedAtDesc(assignmentId).stream()
                .map(ResultResponse::from).toList();
    }
}
