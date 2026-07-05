package az.millers.hcm.businesstrip.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.businesstrip.domain.ExpenseCategory;
import az.millers.hcm.businesstrip.domain.ExpensePolicy;

public interface ExpensePolicyRepository extends JpaRepository<ExpensePolicy, UUID> {

    List<ExpensePolicy> findByTenantIdAndActiveOrderByCategory(String tenantId, boolean active);

    Optional<ExpensePolicy> findByIdAndTenantId(UUID id, String tenantId);

    /**
     * Find matching policy for validation.
     * Ordered by specificity: grade-specific > category-wide.
     */
    @Query("""
        SELECT p FROM ExpensePolicy p
        WHERE p.tenantId = :tenantId
          AND p.active = true
          AND p.category = :category
          AND (p.employeeGrade IS NULL OR p.employeeGrade = :grade)
          AND p.effectiveFrom <= :date
          AND (p.effectiveTo IS NULL OR p.effectiveTo >= :date)
        ORDER BY
          CASE WHEN p.employeeGrade IS NOT NULL THEN 1 ELSE 0 END DESC,
          p.effectiveFrom DESC
    """)
    List<ExpensePolicy> findMatchingPolicies(
            @Param("tenantId") String tenantId,
            @Param("category") ExpenseCategory category,
            @Param("grade") String grade,
            @Param("date") LocalDate date);
}
