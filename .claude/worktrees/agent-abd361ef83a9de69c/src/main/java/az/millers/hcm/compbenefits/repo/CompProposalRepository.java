package az.millers.hcm.compbenefits.repo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.compbenefits.domain.CompProposal;
import az.millers.hcm.compbenefits.domain.CompProposalStatus;

public interface CompProposalRepository extends JpaRepository<CompProposal, UUID> {

    List<CompProposal> findByCycleIdOrderByProposedAtDesc(UUID cycleId);

    List<CompProposal> findByCycleIdAndStatusOrderByProposedAtDesc(
            UUID cycleId, CompProposalStatus status);

    Optional<CompProposal> findByCycleIdAndEmployeeId(UUID cycleId, UUID employeeId);

    List<CompProposal> findByCycleIdAndProposedByOrderByProposedAtDesc(
            UUID cycleId, String proposedBy);

    /**
     * Sum of (proposed - current) across non-rejected proposals in a cycle.
     * Used by the budget meter — Phase 1 counts both DRAFT and SUBMITTED
     * as "in flight" against the pool.
     */
    @Query("""
            select coalesce(sum(p.proposedSalary - p.currentSalary), 0)
              from CompProposal p
             where p.cycleId = :cycleId
               and p.status <> az.millers.hcm.compbenefits.domain.CompProposalStatus.REJECTED
            """)
    BigDecimal committedDelta(@Param("cycleId") UUID cycleId);
}
