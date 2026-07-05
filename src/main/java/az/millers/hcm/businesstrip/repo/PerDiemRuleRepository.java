package az.millers.hcm.businesstrip.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.businesstrip.domain.PerDiemRule;
import az.millers.hcm.businesstrip.domain.TripType;

public interface PerDiemRuleRepository extends JpaRepository<PerDiemRule, UUID> {

    List<PerDiemRule> findByTenantIdAndActiveOrderByDestinationCountryAscDestinationCityAsc(
            String tenantId, boolean active);

    Optional<PerDiemRule> findByIdAndTenantId(UUID id, String tenantId);

    /**
     * Find matching rules for per-diem calculation.
     * Ordered by specificity: city+grade+type > city+grade > city+type > city > country+grade+type > country+grade > country+type > country.
     */
    @Query("""
        SELECT r FROM PerDiemRule r
        WHERE r.tenantId = :tenantId
          AND r.active = true
          AND r.destinationCountry = :country
          AND (r.destinationCity IS NULL OR r.destinationCity = :city)
          AND (r.employeeGrade IS NULL OR r.employeeGrade = :grade)
          AND (r.tripType IS NULL OR r.tripType = :tripType)
          AND r.effectiveFrom <= :date
          AND (r.effectiveTo IS NULL OR r.effectiveTo >= :date)
        ORDER BY
          CASE WHEN r.destinationCity IS NOT NULL THEN 1 ELSE 0 END DESC,
          CASE WHEN r.employeeGrade IS NOT NULL THEN 1 ELSE 0 END DESC,
          CASE WHEN r.tripType IS NOT NULL THEN 1 ELSE 0 END DESC,
          r.effectiveFrom DESC
    """)
    List<PerDiemRule> findMatchingRules(
            @Param("tenantId") String tenantId,
            @Param("country") String country,
            @Param("city") String city,
            @Param("grade") String grade,
            @Param("tripType") TripType tripType,
            @Param("date") LocalDate date);
}
