package az.millers.hcm.staffing.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.staffing.domain.PositionBudget;

/** M244 — versioned budget rows per position. */
public interface PositionBudgetRepository extends JpaRepository<PositionBudget, UUID> {

    List<PositionBudget> findByPositionIdOrderByEffectiveFromDesc(UUID positionId);

    /**
     * Latest budget for {@code positionId} whose effective window contains
     * {@code asOf}. If no row matches (e.g. no budget exists yet), returns
     * empty — callers should treat that as "no budget set yet".
     */
    @Query("""
            select b from PositionBudget b
            where b.positionId = :positionId
              and b.effectiveFrom <= :asOf
              and (b.effectiveTo is null or b.effectiveTo >= :asOf)
            order by b.effectiveFrom desc
            """)
    List<PositionBudget> findActiveAsOf(@Param("positionId") UUID positionId,
                                         @Param("asOf") LocalDate asOf);

    default Optional<PositionBudget> currentBudget(UUID positionId, LocalDate asOf) {
        return findActiveAsOf(positionId, asOf).stream().findFirst();
    }
}
