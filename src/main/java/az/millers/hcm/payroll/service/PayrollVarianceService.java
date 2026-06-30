package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.api.dto.PayrollVarianceResponse;
import az.millers.hcm.payroll.api.dto.PayrollVarianceResponse.EmployeeVariance;
import az.millers.hcm.payroll.api.dto.PayrollVarianceResponse.VarianceSummary;
import az.millers.hcm.payroll.api.dto.YtdSummaryResponse;
import az.millers.hcm.payroll.api.dto.YtdSummaryResponse.EmployeeYtd;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollRunStatus;
import az.millers.hcm.payroll.domain.RunType;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;

@Service
public class PayrollVarianceService {

    private final PayrollRunRepository runRepo;
    private final PayrollResultRepository resultRepo;
    private final EmployeeRepository employeeRepo;

    public PayrollVarianceService(PayrollRunRepository runRepo,
                                   PayrollResultRepository resultRepo,
                                   EmployeeRepository employeeRepo) {
        this.runRepo = runRepo;
        this.resultRepo = resultRepo;
        this.employeeRepo = employeeRepo;
    }

    @Transactional(readOnly = true)
    public PayrollVarianceResponse variance(UUID currentRunId, UUID priorRunId) {
        PayrollRun currentRun = runRepo.findById(currentRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Current run not found"));
        PayrollRun priorRun = runRepo.findById(priorRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Prior run not found"));

        if (currentRun.getRunType() != RunType.REGULAR) {
            throw new BadRequestException("Current run must be a REGULAR run");
        }
        if (priorRun.getRunType() != RunType.REGULAR) {
            throw new BadRequestException("Prior run must be a REGULAR run");
        }

        List<PayrollResult> currentResults = resultRepo.findByRunIdOrderByEmployeeIdAsc(currentRunId);
        List<PayrollResult> priorResults = resultRepo.findByRunIdOrderByEmployeeIdAsc(priorRunId);

        Map<UUID, PayrollResult> priorMap = priorResults.stream()
                .collect(Collectors.toMap(PayrollResult::getEmployeeId, r -> r));
        Map<UUID, PayrollResult> currentMap = currentResults.stream()
                .collect(Collectors.toMap(PayrollResult::getEmployeeId, r -> r));

        List<UUID> allEmployeeIds = new ArrayList<>();
        allEmployeeIds.addAll(currentMap.keySet());
        priorMap.keySet().stream()
                .filter(id -> !currentMap.containsKey(id))
                .forEach(allEmployeeIds::add);

        Map<UUID, Employee> employees = employeeRepo.findAllById(allEmployeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        List<EmployeeVariance> variances = new ArrayList<>();
        BigDecimal totalGrossChange = BigDecimal.ZERO;
        int highVarianceCount = 0;
        int newEmployeeCount = 0;
        int absentEmployeeCount = 0;

        for (UUID empId : allEmployeeIds) {
            PayrollResult prior = priorMap.get(empId);
            PayrollResult current = currentMap.get(empId);
            Employee emp = employees.get(empId);

            if (emp == null) continue;

            List<String> flags = new ArrayList<>();
            BigDecimal priorGross = prior != null ? prior.getGrossAmount() : BigDecimal.ZERO;
            BigDecimal currentGross = current != null ? current.getGrossAmount() : BigDecimal.ZERO;
            BigDecimal grossDelta = currentGross.subtract(priorGross);
            BigDecimal grossDeltaPct = BigDecimal.ZERO;

            if (priorGross.compareTo(BigDecimal.ZERO) > 0) {
                grossDeltaPct = grossDelta.divide(priorGross, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }

            BigDecimal priorNet = prior != null ? prior.getNetAmount() : BigDecimal.ZERO;
            BigDecimal currentNet = current != null ? current.getNetAmount() : BigDecimal.ZERO;
            BigDecimal netDelta = currentNet.subtract(priorNet);

            if (prior == null && current != null) {
                flags.add("NEW_EMPLOYEE");
                newEmployeeCount++;
            }
            if (prior != null && current == null) {
                flags.add("EMPLOYEE_ABSENT");
                absentEmployeeCount++;
            }
            if (prior != null && current != null) {
                if (grossDelta.abs().compareTo(new BigDecimal("0.01")) > 0) {
                    flags.add("SALARY_CHANGE");
                }
                if (current.getBonusAmount().compareTo(prior.getBonusAmount()) > 0) {
                    flags.add("BONUS_ADDED");
                }
                if (current.getDeductionAmount().compareTo(prior.getDeductionAmount()) > 0) {
                    flags.add("DEDUCTION_ADDED");
                }
            }

            if (grossDeltaPct.abs().compareTo(new BigDecimal("10")) > 0) {
                highVarianceCount++;
            }

            totalGrossChange = totalGrossChange.add(grossDelta);

            String name = emp.getFirstName() + " " + emp.getLastName();

            variances.add(new EmployeeVariance(
                    empId,
                    emp.getEmployeeNo(),
                    name,
                    priorGross,
                    currentGross,
                    grossDelta,
                    grossDeltaPct.setScale(2, RoundingMode.HALF_UP),
                    netDelta,
                    flags
            ));
        }

        BigDecimal pctChange = BigDecimal.ZERO;
        BigDecimal priorTotal = priorResults.stream()
                .map(PayrollResult::getGrossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (priorTotal.compareTo(BigDecimal.ZERO) > 0) {
            pctChange = totalGrossChange.divide(priorTotal, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(1, RoundingMode.HALF_UP);
        }

        VarianceSummary summary = new VarianceSummary(
                totalGrossChange.setScale(2, RoundingMode.HALF_UP),
                pctChange,
                highVarianceCount,
                newEmployeeCount,
                absentEmployeeCount
        );

        return new PayrollVarianceResponse(currentRunId, priorRunId, summary, variances);
    }

    @Transactional(readOnly = true)
    public YtdSummaryResponse ytd(int year, UUID employeeId) {
        List<PayrollRun> runs = runRepo.findAllByOrderByPeriodYearDescPeriodMonthDesc();
        List<UUID> paidRunIds = runs.stream()
                .filter(r -> r.getPeriodYear() == year)
                .filter(r -> r.getRunType() == RunType.REGULAR)
                .filter(r -> r.getStatus() == PayrollRunStatus.PAID)
                .map(PayrollRun::getId)
                .toList();

        if (paidRunIds.isEmpty()) {
            return new YtdSummaryResponse(year, List.of());
        }

        Map<UUID, List<PayrollResult>> resultsByEmployee = new HashMap<>();
        for (UUID runId : paidRunIds) {
            List<PayrollResult> results = resultRepo.findByRunIdOrderByEmployeeIdAsc(runId);
            for (PayrollResult r : results) {
                if (employeeId == null || r.getEmployeeId().equals(employeeId)) {
                    resultsByEmployee.computeIfAbsent(r.getEmployeeId(), k -> new ArrayList<>()).add(r);
                }
            }
        }

        List<UUID> employeeIds = new ArrayList<>(resultsByEmployee.keySet());
        Map<UUID, Employee> employees = employeeRepo.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        List<EmployeeYtd> ytdList = new ArrayList<>();
        for (Map.Entry<UUID, List<PayrollResult>> entry : resultsByEmployee.entrySet()) {
            UUID empId = entry.getKey();
            List<PayrollResult> empResults = entry.getValue();
            Employee emp = employees.get(empId);

            if (emp == null) continue;

            BigDecimal totalGross = empResults.stream()
                    .map(PayrollResult::getGrossAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalIncomeTax = empResults.stream()
                    .map(PayrollResult::getIncomeTax)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalDsmf = empResults.stream()
                    .map(PayrollResult::getDsmfEmployee)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalMmi = empResults.stream()
                    .map(PayrollResult::getMmiEmployee)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalUnemployment = empResults.stream()
                    .map(PayrollResult::getUnemplEmployee)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalBonuses = empResults.stream()
                    .map(PayrollResult::getBonusAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalNet = empResults.stream()
                    .map(PayrollResult::getNetAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String name = emp.getFirstName() + " " + emp.getLastName();

            ytdList.add(new EmployeeYtd(
                    empId,
                    emp.getEmployeeNo(),
                    name,
                    totalGross.setScale(2, RoundingMode.HALF_UP),
                    totalIncomeTax.setScale(2, RoundingMode.HALF_UP),
                    totalDsmf.setScale(2, RoundingMode.HALF_UP),
                    totalMmi.setScale(2, RoundingMode.HALF_UP),
                    totalUnemployment.setScale(2, RoundingMode.HALF_UP),
                    totalBonuses.setScale(2, RoundingMode.HALF_UP),
                    totalNet.setScale(2, RoundingMode.HALF_UP),
                    empResults.size()
            ));
        }

        return new YtdSummaryResponse(year, ytdList);
    }
}
