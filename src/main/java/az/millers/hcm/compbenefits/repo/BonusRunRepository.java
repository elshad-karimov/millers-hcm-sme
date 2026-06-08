package az.millers.hcm.compbenefits.repo;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.compbenefits.domain.BonusRun;
import az.millers.hcm.compbenefits.domain.BonusRunStatus;

public interface BonusRunRepository extends JpaRepository<BonusRun, UUID> {

    @Query(value = "SELECT nextval('comp_benefits.bonus_run_no_seq')", nativeQuery = true)
    long nextNoSequence();

    Page<BonusRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<BonusRun> findByStatusOrderByCreatedAtDesc(BonusRunStatus status, Pageable pageable);

    Page<BonusRun> findByCycleIdOrderByCreatedAtDesc(UUID cycleId, Pageable pageable);

    /** Used to prevent duplicate auto-generation on cycle completion. */
    boolean existsByCycleIdAndStatusIn(UUID cycleId, Collection<BonusRunStatus> statuses);
}
