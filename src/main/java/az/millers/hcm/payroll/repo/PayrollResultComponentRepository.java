package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.payroll.domain.PayrollResultComponent;

/**
 * M349 — Per-payslip component snapshot repository.
 */
public interface PayrollResultComponentRepository extends JpaRepository<PayrollResultComponent, UUID> {

    List<PayrollResultComponent> findByResultIdOrderByKindDescAmountDesc(UUID resultId);

    void deleteByResultId(UUID resultId);

    /**
     * Wipe every component snapshot belonging to a run's results. payroll_result is
     * partitioned so there is no ON DELETE CASCADE; the engine calls this BEFORE deleting
     * the run's results so the sub-query can still resolve the result ids.
     */
    @Modifying
    @Query("DELETE FROM PayrollResultComponent c WHERE c.resultId IN "
            + "(SELECT r.id FROM PayrollResult r WHERE r.runId = :runId)")
    void deleteByRunId(@Param("runId") UUID runId);
}
