package az.millers.hcm.common.tenant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Clones per-tenant <b>reference / configuration</b> data from a source tenant
 * (normally {@code 'default'}) to a freshly provisioned tenant (multi-tenancy
 * Phase 4).
 *
 * <p>Transactional / employee data is never copied — a new tenant starts empty
 * of people and history. Only the config seeds a tenant needs to be functional
 * (approval workflows, statutory payroll rules, leave types, holidays, letter
 * templates, rating scales, …) are duplicated.
 *
 * <h3>How it stays generic</h3>
 * The cloner needs no per-table column list and no explicit FK map:
 * <ol>
 *   <li>Tables are cloned in dependency order (parents before children).</li>
 *   <li>Each row is read as a column→value map ({@code SELECT *}); every
 *       {@code UUID} primary key is regenerated and the old→new mapping recorded
 *       in a run-global table.</li>
 *   <li>For every other column, if its value is a {@code UUID} that was a
 *       regenerated PK of an already-cloned row, it is rewritten to the new id —
 *       so any intra-reference foreign key (e.g. {@code workflow_step.definition_id}
 *       → {@code workflow_definition.id}) is remapped automatically.</li>
 *   <li>{@code tenant_id} is set to the target on every inserted row.</li>
 * </ol>
 *
 * <p>Because it uses native SQL, it is unaffected by Hibernate's {@code @TenantId}
 * discriminator (which only filters JPA) — the source rows are read by an
 * explicit {@code WHERE tenant_id = ?} and the target is stamped explicitly.
 */
@Service
public class TenantReferenceSeeder {

    private static final Logger log = LoggerFactory.getLogger(TenantReferenceSeeder.class);

    /**
     * Reference tables cloned for a new tenant, in dependency order (a parent
     * must precede any table that references it so FK remap resolves).
     */
    static final List<String> REFERENCE_TABLES = List.of(
            // approval workflows (definition -> step)
            "workflow.workflow_definition",
            "workflow.workflow_step",
            // payroll statutory + component config
            "payroll.statutory_rule",
            "payroll.salary_component",
            "payroll.payroll_group",
            "payroll.loan_type",
            // leave config (category/group -> type)
            "leave_mgmt.leave_category",
            "leave_mgmt.leave_group",
            "leave_mgmt.leave_type",
            // calendar
            "core_hr.holiday",
            // letters / documents / permissions / org taxonomy
            "hr_letters.letter_template",
            "permission.permission_type",
            "organization.org_unit_type",
            "staffing.reason_master",
            "lifecycle.notice_period_rule",
            "lifecycle.asset_category",
            "recruitment.document_type",
            // performance config (scale -> value)
            "performance.rating_scale",
            "performance.rating_scale_value",
            "performance.goal_type",
            // ehs
            "ehs.ppe_item"
    );

    private final JdbcTemplate jdbc;

    public TenantReferenceSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Copy all configured reference tables from {@code sourceTenant} to
     * {@code targetTenant}. Returns per-table row counts. Runs in the caller's
     * transaction (provisioning is all-or-nothing).
     */
    public Map<String, Integer> copyReferenceData(String sourceTenant, String targetTenant) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<UUID, UUID> idMap = new ConcurrentHashMap<>();
        for (String table : REFERENCE_TABLES) {
            int n = copyTable(table, sourceTenant, targetTenant, idMap);
            counts.put(table, n);
        }
        log.info("Seeded reference data {} -> {}: {}", sourceTenant, targetTenant, counts);
        return counts;
    }

    private int copyTable(String table, String source, String target, Map<UUID, UUID> idMap) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("SELECT * FROM " + table + " WHERE tenant_id = ?", source);
        } catch (org.springframework.dao.DataAccessException e) {
            // Table missing a tenant_id column, or absent — skip rather than fail
            // the whole provisioning. Logged so gaps are visible.
            log.warn("Skipping reference table {} — {}", table, e.getMostSpecificCause().getMessage());
            return 0;
        }
        int inserted = 0;
        for (Map<String, Object> src : rows) {
            Map<String, Object> row = new LinkedHashMap<>(src);

            // 1) regenerate a UUID primary key; record old -> new for FK remap
            Object pk = row.get("id");
            if (pk instanceof UUID oldId) {
                UUID newId = UUID.randomUUID();
                idMap.put(oldId, newId);
                row.put("id", newId);
            } else if (pk instanceof Number) {
                // serial/identity PK — let the sequence assign a fresh value
                row.remove("id");
            }

            // 2) remap any FK column whose UUID value points at an already-cloned row
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if ("id".equals(e.getKey())) continue;
                if (e.getValue() instanceof UUID u && idMap.containsKey(u)) {
                    e.setValue(idMap.get(u));
                }
            }

            // 3) stamp the target tenant
            row.put("tenant_id", target);

            insertRow(table, row);
            inserted++;
        }
        return inserted;
    }

    private void insertRow(String table, Map<String, Object> row) {
        List<String> cols = new ArrayList<>(row.keySet());
        String columnList = String.join(", ", cols);
        String placeholders = String.join(", ", cols.stream().map(c -> "?").toList());
        Object[] values = cols.stream().map(row::get).toArray();
        jdbc.update("INSERT INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ")", values);
    }
}
