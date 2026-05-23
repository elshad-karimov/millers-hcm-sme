package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.compbenefits.domain.BonusRunItem;

public interface BonusRunItemRepository extends JpaRepository<BonusRunItem, UUID> {

    @Query(value = "SELECT nextval('comp_benefits.bonus_run_item_no_seq')", nativeQuery = true)
    long nextItemNoSequence();

    List<BonusRunItem> findByRunIdOrderByCreatedAt(UUID runId);

    void deleteByRunId(UUID runId);
}
