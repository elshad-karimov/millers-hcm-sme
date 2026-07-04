package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.api.dto.GoalCascadeRequest;
import az.millers.hcm.performance.api.dto.GoalResponse;
import az.millers.hcm.performance.api.dto.GoalTreeNode;
import az.millers.hcm.performance.domain.Goal;
import az.millers.hcm.performance.domain.GoalStatus;
import az.millers.hcm.performance.repo.GoalRepository;
import az.millers.hcm.performance.service.GoalTreeMath.GoalRef;
import az.millers.hcm.security.CurrentRequest;

/**
 * M130 — orchestrates the "cascade to my report" action and the tree
 * view endpoint. All math lives in {@link GoalTreeMath} — this class
 * is just glue around the existing {@link GoalRepository}, the new
 * cycle-detection rule, and the audit pass.
 */
@Service
public class GoalCascadeService {

    private static final String MODULE = "PERFORMANCE";
    private static final String ENTITY = "Goal";

    private final GoalRepository goals;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final NamedParameterJdbcTemplate jdbc;

    public GoalCascadeService(GoalRepository goals,
                               EmployeeRepository employees,
                               AuditService audit,
                               CurrentRequest currentRequest,
                               NamedParameterJdbcTemplate jdbc) {
        this.goals = goals;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.jdbc = jdbc;
    }

    /**
     * Copies {@code parentGoalId} as a child for {@code req.employeeId()}
     * within the parent's cycle. Title, description, category, target
     * metric, and due-date clone from the parent. Status starts at
     * {@link GoalStatus#DRAFT} so the report still owns activation.
     */
    @Transactional
    public Goal cascade(UUID parentGoalId, GoalCascadeRequest req) {
        Goal parent = goals.findById(parentGoalId).orElseThrow(
                () -> new ResourceNotFoundException("Parent goal not found: " + parentGoalId));
        if (req.employeeId() == null) {
            throw new BadRequestException("employeeId is required");
        }
        if (req.employeeId().equals(parent.getEmployeeId())) {
            throw new BadRequestException("Cannot cascade a goal onto its own owner");
        }
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }

        Goal child = new Goal();
        child.setGoalNo(String.format("GOAL-%05d", goals.nextNoSequence()));
        child.setCycleId(parent.getCycleId());
        child.setEmployeeId(req.employeeId());
        child.setParentGoalId(parent.getId());
        child.setTitle(parent.getTitle());
        child.setDescription(parent.getDescription());
        child.setCategory(parent.getCategory());
        child.setTargetMetric(parent.getTargetMetric());
        child.setWeightPercent(req.weightPercent() != null
                ? req.weightPercent()
                : parent.getWeightPercent());
        child.setProgressPercent(BigDecimal.ZERO);
        child.setStatus(GoalStatus.DRAFT);
        child.setDueDate(parent.getDueDate());
        child.setCreatedBy(currentRequest.username());
        child.setUpdatedBy(currentRequest.username());
        child.setCascadedBy(currentRequest.username());
        child.setCascadedAt(OffsetDateTime.now());

        Goal saved = goals.save(child);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CASCADE",
                null, GoalResponse.from(saved));
        return saved;
    }

    /**
     * Tree view for one cycle. Loads every goal in the cycle, computes
     * depth + descendant count + alignment % per node, joins employee
     * names in one bulk query.
     *
     * <p>O(n) over goals; the alignment pass is O(n²) worst-case, fine
     * for cycle sizes &lt;~ 5000 which the model targets.
     */
    @Transactional(readOnly = true)
    public List<GoalTreeNode> tree(UUID cycleId) {
        List<Goal> rows = goals.findByCycleIdOrderByEmployeeIdAscCreatedAtAsc(cycleId);
        if (rows.isEmpty()) return List.of();

        // Build GoalRef views once + employee-id → name lookup.
        List<GoalRef> refs = new ArrayList<>(rows.size());
        Map<UUID, GoalRef> byId = new HashMap<>(rows.size() * 2);
        for (Goal g : rows) {
            GoalRef ref = new GoalRef(
                    g.getId(), g.getParentGoalId(), g.getEmployeeId(), g.getCycleId(),
                    g.getWeightPercent(), g.getProgressPercent());
            refs.add(ref);
            byId.put(g.getId(), ref);
        }
        Map<UUID, UUID> parentByGoal = GoalTreeMath.parentByGoal(refs);
        Map<UUID, List<UUID>> childrenByParent = GoalTreeMath.childrenByParent(refs);
        Map<UUID, String> nameByEmp = loadNames(refs.stream().map(GoalRef::employeeId).distinct().toList());

        List<GoalTreeNode> out = new ArrayList<>(rows.size());
        for (Goal g : rows) {
            int depth = GoalTreeMath.depth(g.getId(), parentByGoal::get);
            int descCount = GoalTreeMath.descendantsOf(g.getId(), childrenByParent).size();
            BigDecimal alignment = GoalTreeMath.alignmentPercent(g.getId(), byId, childrenByParent);
            out.add(new GoalTreeNode(
                    g.getId(), g.getParentGoalId(), g.getEmployeeId(),
                    nameByEmp.get(g.getEmployeeId()),
                    g.getGoalNo(), g.getTitle(),
                    g.getWeightPercent(), g.getProgressPercent(),
                    depth, descCount, alignment));
        }
        return out;
    }

    private Map<UUID, String> loadNames(List<UUID> empIds) {
        if (empIds == null || empIds.isEmpty()) return Map.of();
        Map<UUID, String> out = new HashMap<>();
        for (var row : jdbc.queryForList(
                "SELECT id::text AS id, first_name, last_name FROM core_hr.employee WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", empIds))) {
            out.put(UUID.fromString((String) row.get("id")),
                    row.get("first_name") + " " + row.get("last_name"));
        }
        return out;
    }
}
