package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.ReasonCategory;
import az.millers.hcm.staffing.domain.ReasonMaster;

/** M259 — lookup repo for reason masters (PRD §22). */
public interface ReasonMasterRepository extends JpaRepository<ReasonMaster, UUID> {

    /** Reasons in one category, active first, ordered for display. */
    List<ReasonMaster> findByCategoryAndActiveTrueOrderBySortOrderAscLabelAsc(
            ReasonCategory category);

    /** Admin view — all reasons in a category (including inactive). */
    List<ReasonMaster> findByCategoryOrderBySortOrderAscLabelAsc(ReasonCategory category);
}
