package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.leave.api.dto.LeaveLiabilityReport;
import az.millers.hcm.leave.api.dto.LeaveLiabilityRow;
import az.millers.hcm.leave.domain.LeaveBalance;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.repo.LeaveBalanceRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.organization.domain.OrgUnit;
import az.millers.hcm.organization.repo.OrgUnitRepository;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;

@Service
public class LeaveLiabilityService {

    private static final int DEFAULT_WORKING_DAYS =
            az.millers.hcm.common.LeaveDayValuation.DEFAULT_WORKING_DAYS_PER_MONTH;

    private final LeaveBalanceRepository balances;
    private final LeaveTypeRepository types;
    private final EmployeeRepository employees;
    private final EmployeeCompensationRepository compensations;
    private final OrgUnitRepository orgUnits;

    public LeaveLiabilityService(LeaveBalanceRepository balances,
                                  LeaveTypeRepository types,
                                  EmployeeRepository employees,
                                  EmployeeCompensationRepository compensations,
                                  OrgUnitRepository orgUnits) {
        this.balances = balances;
        this.types = types;
        this.employees = employees;
        this.compensations = compensations;
        this.orgUnits = orgUnits;
    }

    @Transactional(readOnly = true)
    public LeaveLiabilityReport generate(int year, int workingDaysPerMonth) {
        if (workingDaysPerMonth <= 0) {
            workingDaysPerMonth = DEFAULT_WORKING_DAYS;
        }

        List<LeaveBalance> allBalances = balances.findByYearOrderByEmployeeIdAscLeaveTypeIdAsc(year);

        Map<UUID, LeaveType> typeMap = types.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, t -> t));

        Map<UUID, Employee> employeeMap = employees.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        Map<UUID, OrgUnit> orgUnitMap = orgUnits.findAll().stream()
                .collect(Collectors.toMap(OrgUnit::getId, o -> o));

        LocalDate today = LocalDate.now();
        int wdpm = workingDaysPerMonth;

        List<LeaveLiabilityRow> rows = new ArrayList<>();
        BigDecimal totalLiability = BigDecimal.ZERO;

        for (LeaveBalance balance : allBalances) {
            BigDecimal remaining = balance.remaining();
            if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Employee emp = employeeMap.get(balance.getEmployeeId());
            if (emp == null) continue;

            LeaveType type = typeMap.get(balance.getLeaveTypeId());
            if (type == null) continue;

            // Only paid leave creates a liability
            if (!type.isPaid()) continue;

            OrgUnit dept = emp.getOrgUnitId() != null ? orgUnitMap.get(emp.getOrgUnitId()) : null;

            BigDecimal monthlySalary = compensations
                    .findActiveOn(balance.getEmployeeId(), today)
                    .map(EmployeeCompensation::getMonthlyBaseSalary)
                    .orElse(BigDecimal.ZERO);

            BigDecimal dailyRate = az.millers.hcm.common.LeaveDayValuation.dailyRate(monthlySalary, wdpm);
            BigDecimal liability = remaining.multiply(dailyRate).setScale(2, RoundingMode.HALF_UP);

            rows.add(new LeaveLiabilityRow(
                    emp.getId(),
                    emp.getEmployeeNo(),
                    emp.getFirstName() + " " + emp.getLastName(),
                    dept != null ? dept.getName() : "—",
                    type.getId(),
                    type.getCode(),
                    type.getName(),
                    year,
                    remaining,
                    monthlySalary,
                    dailyRate,
                    liability
            ));

            totalLiability = totalLiability.add(liability);
        }

        return new LeaveLiabilityReport(year, wdpm, totalLiability, rows);
    }
}
