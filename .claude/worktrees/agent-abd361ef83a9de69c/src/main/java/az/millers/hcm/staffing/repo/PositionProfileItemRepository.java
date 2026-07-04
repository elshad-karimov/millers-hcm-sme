package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.PositionProfileItem;

public interface PositionProfileItemRepository extends JpaRepository<PositionProfileItem, UUID> {

    List<PositionProfileItem> findByPositionIdOrderByItemTypeAscSortOrderAscLabelAsc(UUID positionId);
    void deleteByPositionId(UUID positionId);
}
