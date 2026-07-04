package az.millers.hcm.reporting.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.leave.repo.LeaveRequestRepository;
import az.millers.hcm.lifecycle.domain.EmploymentContract;
import az.millers.hcm.lifecycle.domain.ProbationReview;
import az.millers.hcm.lifecycle.repo.EmploymentContractRepository;
import az.millers.hcm.lifecycle.repo.ProbationReviewRepository;
import az.millers.hcm.corehr.repo.EmployeeCertificationRepository;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.CertificationExpiringReport;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.CertificationExpiringRow;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.ContractExpiringReport;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.ContractExpiringRow;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.EmpMgmtSummary;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.ProbationDueReport;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.ProbationDueRow;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.RehireReport;
import az.millers.hcm.reporting.api.dto.EmpMgmtDtos.RehireRow;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * Employee Management report family (M80 / P2-29/30/31/33). No new tables;
 * each report aggregates existing data — certifications/contracts/probation
 * reviews from the M62-M79 surface, employees themselves for the rehire
 * roll, and (in {@link #summary}) cross-cuts from the broader system.
 */
@Service
public class EmpMgmtReportsService {

    private static final int DEFAULT_LOOKAHEAD_DAYS = 60;

    private final EmployeeRepository employees;
    private final ProbationReviewRepository probationReviews;
    private final EmploymentContractRepository contracts;
    private final EmployeeCertificationRepository certifications;
    private final LeaveRequestRepository leaveRequests;
    private final AccessScopeService scope;

    public EmpMgmtReportsService(EmployeeRepository employees,
                                  ProbationReviewRepository probationReviews,
                                  EmploymentContractRepository contracts,
                                  EmployeeCertificationRepository certifications,
                                  LeaveRequestRepository leaveRequests,
                                  AccessScopeService scope) {
        this.employees = employees;
        this.probationReviews = probationReviews;
        this.contracts = contracts;
        this.certifications = certifications;
        this.leaveRequests = leaveRequests;
        this.scope = scope;
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProbationDueReport probationDue(Integer lookaheadDays) {
        LocalDate today = LocalDate.now();
        LocalDate by = today.plusDays(nullSafe(lookaheadDays));
        var ids = scopedIds();
        if (ids != null && ids.isEmpty()) {
            return new ProbationDueReport(today, List.of());
        }
        List<ProbationReview> all = ids == null
                ? probationReviews.findPendingForEmployees(allEmployeeIds(), by)
                : probationReviews.findPendingForEmployees(ids, by);
        Map<UUID, Employee> empById = loadEmployees(all.stream().map(ProbationReview::getEmployeeId).toList());
        List<ProbationDueRow> rows = all.stream()
                .map(r -> {
                    Employee e = empById.get(r.getEmployeeId());
                    return new ProbationDueRow(
                            r.getEmployeeId(),
                            e == null ? null : e.getEmployeeNo(),
                            e == null ? null : (e.getFirstName() + " " + e.getLastName()),
                            r.getId(),
                            r.getScheduledDate(),
                            r.getReviewType().name(),
                            r.getStatus().name(),
                            (int) ChronoUnit.DAYS.between(today, r.getScheduledDate()));
                })
                .toList();
        return new ProbationDueReport(today, rows);
    }

    @Transactional(readOnly = true)
    public ContractExpiringReport contractsExpiring(Integer lookaheadDays) {
        LocalDate today = LocalDate.now();
        int look = nullSafe(lookaheadDays);
        LocalDate by = today.plusDays(look);
        var ids = scopedIds();
        if (ids != null && ids.isEmpty()) {
            return new ContractExpiringReport(today, look, List.of());
        }
        List<EmploymentContract> all = contracts.findActiveExpiringForEmployees(
                ids == null ? allEmployeeIds() : ids, by);
        Map<UUID, Employee> empById = loadEmployees(all.stream().map(EmploymentContract::getEmployeeId).toList());
        List<ContractExpiringRow> rows = all.stream()
                .map(c -> {
                    Employee e = empById.get(c.getEmployeeId());
                    return new ContractExpiringRow(
                            c.getEmployeeId(),
                            e == null ? null : e.getEmployeeNo(),
                            e == null ? null : (e.getFirstName() + " " + e.getLastName()),
                            c.getId(),
                            c.getContractNo(),
                            c.getEndDate(),
                            c.getContractType() == null ? null : c.getContractType().name(),
                            (int) ChronoUnit.DAYS.between(today, c.getEndDate()));
                })
                .toList();
        return new ContractExpiringReport(today, look, rows);
    }

    @Transactional(readOnly = true)
    public CertificationExpiringReport certificationsExpiring(Integer lookaheadDays) {
        LocalDate today = LocalDate.now();
        int look = nullSafe(lookaheadDays);
        LocalDate by = today.plusDays(look);
        var ids = scopedIds();
        if (ids != null && ids.isEmpty()) {
            return new CertificationExpiringReport(today, look, List.of());
        }
        var all = certifications.findExpiringBy(by, ids);
        Map<UUID, Employee> empById = loadEmployees(all.stream().map(c -> c.getEmployeeId()).toList());
        var rows = all.stream()
                .map(c -> {
                    Employee e = empById.get(c.getEmployeeId());
                    return new CertificationExpiringRow(
                            c.getEmployeeId(),
                            e == null ? null : e.getEmployeeNo(),
                            e == null ? null : (e.getFirstName() + " " + e.getLastName()),
                            c.getId(),
                            c.getCertificationName(),
                            c.getExpiryDate(),
                            (int) ChronoUnit.DAYS.between(today, c.getExpiryDate()));
                })
                .toList();
        return new CertificationExpiringReport(today, look, rows);
    }

    @Transactional(readOnly = true)
    public RehireReport recentRehires(Integer limit) {
        int cap = limit == null || limit < 1 || limit > 500 ? 100 : limit;
        var ids = scopedIds();
        if (ids != null && ids.isEmpty()) {
            return new RehireReport(cap, List.of());
        }
        var rehired = employees.findRehired(ids);
        if (rehired.size() > cap) rehired = rehired.subList(0, cap);
        var rows = rehired.stream()
                .map(e -> new RehireRow(
                        e.getId(), e.getEmployeeNo(),
                        e.getFirstName() + " " + e.getLastName(),
                        e.getPreviousEmployeeId(),
                        e.getHireDate(),
                        e.getRehireReason()))
                .toList();
        return new RehireReport(cap, rows);
    }

    @Transactional(readOnly = true)
    public EmpMgmtSummary summary() {
        LocalDate today = LocalDate.now();
        LocalDate ahead = today.plusDays(DEFAULT_LOOKAHEAD_DAYS);
        var ids = scopedIds();

        // Headcount + on-probation pull from the existing repo (cheap on
        // typical HCM sizes). For scoped callers we narrow to their visible
        // set so the cards line up with the rest of their view.
        long headcount;
        long onProbation;
        if (ids == null) {
            headcount = employees.count();
            onProbation = employees.findIdsByEmploymentStatus(EmploymentStatus.ON_PROBATION).size();
        } else {
            headcount = ids.size();
            onProbation = employees.findIdsByEmploymentStatus(EmploymentStatus.ON_PROBATION)
                    .stream().filter(ids::contains).count();
        }

        Collection<UUID> probeIds = ids == null ? allEmployeeIds() : ids;
        long onLeaveToday = probeIds.isEmpty()
                ? 0L
                : leaveRequests.findApprovedOnFor(probeIds, today).size();
        long probationDue = probeIds.isEmpty()
                ? 0L
                : probationReviews.findPendingForEmployees(probeIds, ahead).size();
        long contractsEnding = probeIds.isEmpty()
                ? 0L
                : contracts.findActiveExpiringForEmployees(probeIds, ahead).size();
        long certsExpiring = probeIds.isEmpty()
                ? 0L
                : certifications.findExpiringBy(ahead, ids).size();
        long unverified = employees.countUnverifiedIdentifications(ids);

        return new EmpMgmtSummary(
                headcount, onProbation, onLeaveToday,
                probationDue, contractsEnding, certsExpiring,
                unverified,
                // Pending personal-info changes lives in a different module —
                // we surface 0 here and let the dedicated queue be the source
                // of truth (avoids dragging another repo dependency in just
                // for one card). UI can fetch the queue count itself.
                0L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Set<UUID> scopedIds() {
        return scope.scopeOrNullForCurrentUser();
    }

    private List<UUID> allEmployeeIds() {
        // Cheap for HCM sizes — fewer than 10⁵ rows by design (PRD §15).
        return employees.findAll().stream().map(Employee::getId).toList();
    }

    private Map<UUID, Employee> loadEmployees(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, Employee> out = new HashMap<>();
        employees.findAllById(ids).forEach(e -> out.put(e.getId(), e));
        return out;
    }

    private static int nullSafe(Integer days) {
        if (days == null || days < 1) return DEFAULT_LOOKAHEAD_DAYS;
        return Math.min(days, 365);
    }
}
