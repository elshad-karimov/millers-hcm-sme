package az.millers.hcm.payroll.repo;

import az.millers.hcm.payroll.domain.LaborRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * M485: Labor rate repository.
 */
@Repository
public interface LaborRateRepository extends JpaRepository<LaborRate, UUID> {

    List<LaborRate> findByTenantIdOrderByEffectiveFromDesc(String tenantId);

    Optional<LaborRate> findByIdAndTenantId(UUID id, String tenantId);

    /**
     * Find effective rate for position on given date (latest effective <= date).
     */
    @Query("""
        SELECT r FROM LaborRate r
        WHERE r.tenantId = :tenantId
          AND r.positionId = :positionId
          AND r.effectiveFrom <= :date
          AND (r.effectiveTo IS NULL OR r.effectiveTo >= :date)
        ORDER BY r.effectiveFrom DESC
        LIMIT 1
        """)
    Optional<LaborRate> findEffectiveRateForPosition(@Param("tenantId") String tenantId,
                                                     @Param("positionId") UUID positionId,
                                                     @Param("date") LocalDate date);

    /**
     * Find effective rate for grade on given date.
     */
    @Query("""
        SELECT r FROM LaborRate r
        WHERE r.tenantId = :tenantId
          AND r.gradeId = :gradeId
          AND r.effectiveFrom <= :date
          AND (r.effectiveTo IS NULL OR r.effectiveTo >= :date)
        ORDER BY r.effectiveFrom DESC
        LIMIT 1
        """)
    Optional<LaborRate> findEffectiveRateForGrade(@Param("tenantId") String tenantId,
                                                  @Param("gradeId") UUID gradeId,
                                                  @Param("date") LocalDate date);
}
