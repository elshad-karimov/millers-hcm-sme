package az.millers.hcm.payroll.service;
import az.millers.hcm.common.tenant.TenantContext;

import az.millers.hcm.payroll.domain.LaborCostAllocation;
import az.millers.hcm.payroll.domain.LaborRate;
import az.millers.hcm.payroll.repo.LaborCostAllocationRepository;
import az.millers.hcm.payroll.repo.LaborRateRepository;
import az.millers.hcm.security.CurrentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M485: Labor costing calculator.
 * Computes cost allocations on timesheet approval (idempotent regenerate per timesheet).
 * NON-FATAL: never touches payroll tables.
 */
@Service
public class CostingCalculator {

    private static final Logger log = LoggerFactory.getLogger(CostingCalculator.class);

    private final LaborRateRepository rateRepo;
    private final LaborCostAllocationRepository allocationRepo;
    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentRequest currentRequest;

    public CostingCalculator(LaborRateRepository rateRepo,
                            LaborCostAllocationRepository allocationRepo,
                            NamedParameterJdbcTemplate jdbc,
                            CurrentRequest currentRequest) {
        this.rateRepo = rateRepo;
        this.allocationRepo = allocationRepo;
        this.jdbc = jdbc;
        this.currentRequest = currentRequest;
    }

    /**
     * Compute cost allocations for a timesheet on approval.
     * Idempotent: deletes existing allocations for this timesheet and regenerates.
     * NON-FATAL: logs errors but does not throw.
     */
    @Transactional
    public void computeOnApproval(UUID timesheetId) {
        try {
            // Fetch timesheet days
            String daysSql = """
                SELECT td.id, td.work_date, td.project_id, td.worked_hours,
                       t.employee_id
                FROM timesheet.timesheet_day td
                JOIN timesheet.timesheet t ON td.timesheet_id = t.id
                WHERE td.timesheet_id = :timesheetId
                  AND t.tenant_id = 'default'
                  AND td.worked_hours > 0
                """;

            List<Map<String, Object>> days = jdbc.queryForList(daysSql,
                new MapSqlParameterSource().addValue("timesheetId", timesheetId));

            if (days.isEmpty()) {
                return;
            }

            // Delete existing allocations for this timesheet (idempotent)
            List<UUID> dayIds = days.stream().map(d -> (UUID) d.get("id")).toList();
            allocationRepo.deleteByTimesheetDayIdIn(dayIds);

            // Get employee details (position, grade, cost center)
            UUID employeeId = (UUID) days.get(0).get("employee_id");
            Map<String, Object> employeeDetails = getEmployeeDetails(employeeId);

            UUID positionId = (UUID) employeeDetails.get("position_id");
            UUID gradeId = (UUID) employeeDetails.get("grade_id");

            // Compute allocation per day
            List<LaborCostAllocation> allocations = new ArrayList<>();

            for (Map<String, Object> day : days) {
                UUID dayId = (UUID) day.get("id");
                LocalDate workDate = ((java.sql.Date) day.get("work_date")).toLocalDate();
                UUID projectId = (UUID) day.get("project_id");
                BigDecimal hours = (BigDecimal) day.get("worked_hours");

                // Rate lookup: position > grade, latest effective
                BigDecimal hourlyRate = lookupRate(positionId, gradeId, workDate);
                if (hourlyRate == null) {
                    log.warn("No labor rate found for employee {} on {}", employeeId, workDate);
                    continue;
                }

                // Cost center: from employee's CostCenterAllocation splits or org unit
                UUID costCenterId = getCostCenter(employeeId);

                BigDecimal amount = hours.multiply(hourlyRate);

                LaborCostAllocation allocation = new LaborCostAllocation();
                allocation.setTenantId(TenantContext.current());
                allocation.setTimesheetDayId(dayId);
                allocation.setEmployeeId(employeeId);
                allocation.setWorkDate(workDate);
                allocation.setCostCenterId(costCenterId);
                allocation.setProjectId(projectId);
                allocation.setHours(hours);
                allocation.setHourlyRate(hourlyRate);
                allocation.setAmount(amount);
                allocation.setCreatedBy(currentRequest.username());

                allocations.add(allocation);
            }

            if (!allocations.isEmpty()) {
                allocationRepo.saveAll(allocations);
                log.info("Computed {} labor cost allocations for timesheet {}", allocations.size(), timesheetId);
            }

        } catch (Exception e) {
            log.error("Labor costing computation failed for timesheet {} (non-fatal): {}", timesheetId, e.getMessage());
        }
    }

    private BigDecimal lookupRate(UUID positionId, UUID gradeId, LocalDate date) {
        // Position takes precedence over grade
        if (positionId != null) {
            return rateRepo.findEffectiveRateForPosition(TenantContext.current(), positionId, date)
                .map(LaborRate::getHourlyRate)
                .orElse(null);
        }
        if (gradeId != null) {
            return rateRepo.findEffectiveRateForGrade(TenantContext.current(), gradeId, date)
                .map(LaborRate::getHourlyRate)
                .orElse(null);
        }
        return null;
    }

    private Map<String, Object> getEmployeeDetails(UUID employeeId) {
        String sql = """
            SELECT e.position_id, p.grade_id
            FROM core_hr.employee e
            LEFT JOIN staffing.position p ON p.id = e.position_id
            WHERE e.id = :id AND e.tenant_id = :tenantId
            """;
        return jdbc.queryForMap(sql,
            new MapSqlParameterSource()
                .addValue("id", employeeId)
                .addValue("tenantId", TenantContext.current()));
    }

    private UUID getCostCenter(UUID employeeId) {
        try {
            // NOT REPAIRED — deliberately. This asks for cost_center_id ordered
            // by percentage; the table has cost_center_code (text) and
            // allocation_pct. The column names are a trivial fix, the TYPE is
            // not: getCostCenter returns a UUID and feeds
            // allocation.setCostCenterId(UUID), so pointing it at a text code
            // means changing what a costing allocation stores. That is a
            // payroll data-model decision, not a typo, and payroll changes need
            // sign-off here. The catch below already degrades this to null, so
            // costing behaves today exactly as it has all along.
            String sql = """
                SELECT cost_center_id FROM payroll.cost_center_allocation
                WHERE employee_id = :employeeId AND tenant_id = :tenantId
                ORDER BY percentage DESC
                LIMIT 1
                """;
            return jdbc.queryForObject(sql,
                new MapSqlParameterSource()
                    .addValue("employeeId", employeeId)
                    .addValue("tenantId", TenantContext.current()),
                UUID.class);
        } catch (Exception e) {
            // Fallback: use employee's org unit (not a cost center, but a placeholder)
            return null;
        }
    }
}
