package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.payroll.domain.ErpExport;

public interface ErpExportRepository extends JpaRepository<ErpExport, UUID> {

    List<ErpExport> findByRunIdOrderByCreatedAtDesc(UUID runId);

    List<ErpExport> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(e.exportNo, 5) AS int)), 0) FROM ErpExport e")
    int findMaxSeq();
}
