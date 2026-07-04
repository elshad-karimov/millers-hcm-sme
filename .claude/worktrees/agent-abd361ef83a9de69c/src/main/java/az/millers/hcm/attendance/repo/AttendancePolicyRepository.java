package az.millers.hcm.attendance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.attendance.domain.AttendancePolicy;

public interface AttendancePolicyRepository extends JpaRepository<AttendancePolicy, UUID> {

    List<AttendancePolicy> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);

    Optional<AttendancePolicy> findByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(UUID tenantId, String code, UUID id);

    /**
     * Resolve the most specific active policy for an employee.
     * Priority: (dept + type) > dept-only > type-only > catch-all.
     * Returns the first match ordered by specificity score DESC.
     */
    @Query("""
            SELECT p FROM AttendancePolicy p
            WHERE p.tenantId = :tenantId
              AND p.active = true
              AND (p.departmentId IS NULL OR p.departmentId = :departmentId)
              AND (p.employmentType IS NULL OR p.employmentType = :employmentType)
            ORDER BY
              (CASE WHEN p.departmentId IS NOT NULL THEN 2 ELSE 0 END +
               CASE WHEN p.employmentType IS NOT NULL THEN 1 ELSE 0 END) DESC,
              p.name ASC
            LIMIT 1
            """)
    Optional<AttendancePolicy> findBestMatch(
            @Param("tenantId") UUID tenantId,
            @Param("departmentId") UUID departmentId,
            @Param("employmentType") String employmentType);
}
