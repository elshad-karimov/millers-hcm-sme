package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.EmployeeApprovalLimit;

/** M261 — employee approval limit repo (PRD §27). */
public interface EmployeeApprovalLimitRepository extends JpaRepository<EmployeeApprovalLimit, UUID> {

    /** All limits for an employee, most recent first. */
    List<EmployeeApprovalLimit> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);

    /** Active limits only (effective_to IS NULL). Used by the SPA panel + grant cleanup. */
    List<EmployeeApprovalLimit> findByEmployeeIdAndEffectiveToIsNullOrderByLimitTypeAsc(UUID employeeId);
}
