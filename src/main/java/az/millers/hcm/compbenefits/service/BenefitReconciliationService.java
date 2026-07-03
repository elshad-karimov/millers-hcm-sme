package az.millers.hcm.compbenefits.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.BenefitReconcileDtos.FileRow;
import az.millers.hcm.compbenefits.api.dto.BenefitReconcileDtos.ReconcileLine;
import az.millers.hcm.compbenefits.api.dto.BenefitReconcileDtos.ReconcileResponse;
import az.millers.hcm.compbenefits.domain.BenefitEnrollment;
import az.millers.hcm.compbenefits.domain.BenefitPlan;
import az.millers.hcm.compbenefits.domain.BenefitProvider;
import az.millers.hcm.compbenefits.domain.EnrollmentStatus;
import az.millers.hcm.compbenefits.repo.BenefitEnrollmentRepository;
import az.millers.hcm.compbenefits.repo.BenefitPlanRepository;
import az.millers.hcm.compbenefits.repo.BenefitProviderRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;

/**
 * HCM_11 M384 — reconcile a provider's roster file against the system's active enrollments
 * in that provider's plans. Stateless: the SPA parses the file (CSV) and posts the rows;
 * the service returns matched / amount-mismatch / missing-in-file / extra-in-file lines.
 * Members are matched on employee number; amounts compare total monthly contribution.
 */
@Service
public class BenefitReconciliationService {

    private final BenefitProviderRepository providers;
    private final BenefitPlanRepository plans;
    private final BenefitEnrollmentRepository enrollments;
    private final EmployeeRepository employees;

    public BenefitReconciliationService(BenefitProviderRepository providers,
                                        BenefitPlanRepository plans,
                                        BenefitEnrollmentRepository enrollments,
                                        EmployeeRepository employees) {
        this.providers = providers;
        this.plans = plans;
        this.enrollments = enrollments;
        this.employees = employees;
    }

    private record Member(String name, BigDecimal amount) {}

    @Transactional(readOnly = true)
    public ReconcileResponse reconcile(UUID providerId, List<FileRow> rows) {
        BenefitProvider provider = providers.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Benefit provider not found: " + providerId));

        // System side: active enrollments in this provider's plans, aggregated per employee.
        List<UUID> providerPlanIds = plans.findAll().stream()
                .filter(p -> providerId.equals(p.getProviderId()))
                .map(BenefitPlan::getId).toList();
        Map<String, Member> systemByEmpNo = new HashMap<>();
        Map<UUID, Employee> empCache = new HashMap<>();
        for (UUID planId : providerPlanIds) {
            for (BenefitEnrollment e : enrollments.findByPlanIdAndStatus(planId, EnrollmentStatus.ENROLLED)) {
                Employee emp = empCache.computeIfAbsent(e.getEmployeeId(),
                        id -> employees.findById(id).orElse(null));
                if (emp == null || emp.getEmployeeNo() == null) continue;
                String key = emp.getEmployeeNo().trim();
                BigDecimal amt = nz(e.getEmployerContribution()).add(nz(e.getEmployeeContribution()));
                Member cur = systemByEmpNo.get(key);
                systemByEmpNo.put(key, new Member((emp.getFirstName() + " " + emp.getLastName()).trim(),
                        (cur == null ? BigDecimal.ZERO : cur.amount()).add(amt)));
            }
        }

        // File side: keyed by reference (employee number).
        Map<String, BigDecimal> fileByRef = new HashMap<>();
        for (FileRow r : rows) {
            if (r.reference() == null || r.reference().isBlank()) continue;
            fileByRef.merge(r.reference().trim(), nz(r.amount()), BigDecimal::add);
        }

        List<ReconcileLine> lines = new ArrayList<>();
        int matched = 0, mismatch = 0, missingInFile = 0, extraInFile = 0;
        BigDecimal systemTotal = BigDecimal.ZERO, fileTotal = BigDecimal.ZERO;

        Set<String> allRefs = new HashSet<>();
        allRefs.addAll(systemByEmpNo.keySet());
        allRefs.addAll(fileByRef.keySet());
        for (String ref : allRefs.stream().sorted().toList()) {
            Member sys = systemByEmpNo.get(ref);
            BigDecimal fileAmt = fileByRef.get(ref);
            if (sys != null) systemTotal = systemTotal.add(sys.amount());
            if (fileAmt != null) fileTotal = fileTotal.add(fileAmt);

            String result;
            if (sys != null && fileAmt != null) {
                if (sys.amount().compareTo(fileAmt) == 0) { result = "MATCHED"; matched++; }
                else { result = "AMOUNT_MISMATCH"; mismatch++; }
            } else if (sys != null) {
                result = "MISSING_IN_FILE"; missingInFile++;
            } else {
                result = "EXTRA_IN_FILE"; extraInFile++;
            }
            lines.add(new ReconcileLine(ref, sys == null ? null : sys.name(),
                    sys == null ? null : sys.amount(), fileAmt, result));
        }

        return new ReconcileResponse(provider.getName(), systemByEmpNo.size(), fileByRef.size(),
                matched, mismatch, missingInFile, extraInFile, systemTotal, fileTotal, lines);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
