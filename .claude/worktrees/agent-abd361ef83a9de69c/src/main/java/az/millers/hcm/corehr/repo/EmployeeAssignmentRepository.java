package az.millers.hcm.corehr.repo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.corehr.domain.AssignmentType;
import az.millers.hcm.corehr.domain.EmployeeAssignment;

public interface EmployeeAssignmentRepository
        extends JpaRepository<EmployeeAssignment, UUID> {

    /** Full assignment history for the employee — newest first. */
    List<EmployeeAssignment> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);

    /** Open (effective_to IS NULL) assignments — what's currently in force. */
    @Query("""
            select a from EmployeeAssignment a
            where a.employeeId = :employeeId
              and a.effectiveTo is null
            order by a.assignmentType, a.effectiveFrom
            """)
    List<EmployeeAssignment> findOpenForEmployee(UUID employeeId);

    /** The currently-open PRIMARY assignment, if any. */
    @Query("""
            select a from EmployeeAssignment a
            where a.employeeId = :employeeId
              and a.assignmentType = 'PRIMARY'
              and a.effectiveTo is null
            """)
    Optional<EmployeeAssignment> findOpenPrimary(UUID employeeId);

    /**
     * Sum of allocation_percent across currently-open assignments — used by
     * the service-layer validator to confirm "total allocation ≤ 100%".
     * Excludes the row being updated.
     */
    @Query("""
            select coalesce(sum(a.allocationPercent), 0)
            from EmployeeAssignment a
            where a.employeeId = :employeeId
              and a.effectiveTo is null
              and (:excludeId is null or a.id <> :excludeId)
            """)
    BigDecimal sumOpenAllocationForEmployee(UUID employeeId, UUID excludeId);

    /** Employees currently assigned to a given position — for org-chart queries. */
    @Query("""
            select a from EmployeeAssignment a
            where a.positionId = :positionId
              and a.effectiveTo is null
            order by a.assignmentType, a.effectiveFrom
            """)
    List<EmployeeAssignment> findOpenForPosition(UUID positionId);

    /** Backward-compat — find by employeeId + type for service-layer existence checks. */
    Optional<EmployeeAssignment> findByEmployeeIdAndAssignmentTypeAndEffectiveToIsNull(
            UUID employeeId, AssignmentType assignmentType);
}
