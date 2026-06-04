package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionStatus;
import az.millers.hcm.staffing.domain.VacancyState;

public interface PositionRepository extends JpaRepository<Position, UUID> {

    @Query(value = "SELECT nextval('staffing.position_code_seq')", nativeQuery = true)
    long nextPositionCodeSequence();

    boolean existsByCode(String code);

    Page<Position> findByOrgUnitId(UUID orgUnitId, Pageable pageable);

    Page<Position> findByVacancyState(VacancyState state, Pageable pageable);

    Page<Position> findByStatus(PositionStatus status, Pageable pageable);

    Page<Position> findByTitleContainingIgnoreCaseOrCodeContainingIgnoreCase(
            String title, String code, Pageable pageable);

    /** All ACTIVE positions — used by the M109 reconciliation walker. */
    List<Position> findByStatus(PositionStatus status);

    /** All ACTIVE positions grouped by org unit — for the control dashboard. */
    List<Position> findByStatusOrderByOrgUnitLabelAscTitleAsc(PositionStatus status);
}
