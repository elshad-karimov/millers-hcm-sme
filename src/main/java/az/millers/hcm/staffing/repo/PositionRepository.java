package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionStatus;
import az.millers.hcm.staffing.domain.VacancyState;

public interface PositionRepository extends JpaRepository<Position, UUID> {

    @Query(value = "SELECT nextval('staffing.position_code_seq')", nativeQuery = true)
    long nextPositionCodeSequence();

    boolean existsByTenantIdAndCode(String tenantId, String code);

    List<Position> findByTenantId(String tenantId);

    Page<Position> findByTenantId(String tenantId, Pageable pageable);

    Page<Position> findByTenantIdAndOrgUnitId(String tenantId, UUID orgUnitId, Pageable pageable);

    Page<Position> findByTenantIdAndVacancyState(String tenantId, VacancyState state, Pageable pageable);

    Page<Position> findByTenantIdAndStatus(String tenantId, PositionStatus status, Pageable pageable);

    @Query("""
            select p from Position p
            where p.tenantId = :tenantId
              and (lower(p.title) like lower(concat('%', :search, '%'))
                   or lower(p.code) like lower(concat('%', :search, '%')))
            """)
    Page<Position> searchByTitleOrCode(@Param("tenantId") String tenantId,
                                       @Param("search") String search,
                                       Pageable pageable);

    /** All ACTIVE positions — used by the M109 reconciliation walker. */
    List<Position> findByTenantIdAndStatus(String tenantId, PositionStatus status);

    /** All ACTIVE positions grouped by org unit — for the control dashboard. */
    List<Position> findByTenantIdAndStatusOrderByOrgUnitLabelAscTitleAsc(String tenantId, PositionStatus status);

    /**
     * M257 — all critical positions, highest-impact first.
     * Consumed by the M103 succession bench-depth "Critical Roles at Risk"
     * report to flag positions without named successors.
     */
    List<Position> findByTenantIdAndCriticalFlagTrueOrderByBusinessImpactScoreDescTitleAsc(String tenantId);
}
