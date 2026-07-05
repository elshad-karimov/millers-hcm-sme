package az.millers.hcm.workflow.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.workflow.domain.WorkflowDefinition;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {

    Optional<WorkflowDefinition> findByCode(String code);

    List<WorkflowDefinition> findByActiveTrueOrderByCodeAsc();

    /**
     * M442 — Find the effective workflow definition version for a code and date.
     * Picks the row where the version window contains the date; highest version wins ties.
     */
    @Query("""
        SELECT wd FROM WorkflowDefinition wd
        WHERE wd.code = :code
          AND wd.active = true
          AND (wd.effectiveFrom IS NULL OR wd.effectiveFrom <= :asOfDate)
          AND (wd.effectiveTo IS NULL OR wd.effectiveTo >= :asOfDate)
        ORDER BY wd.version DESC
        """)
    Optional<WorkflowDefinition> findEffectiveVersion(@Param("code") String code, @Param("asOfDate") LocalDate asOfDate);
}
