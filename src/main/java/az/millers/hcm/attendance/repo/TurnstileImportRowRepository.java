package az.millers.hcm.attendance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.attendance.domain.TurnstileImportRow;

public interface TurnstileImportRowRepository extends JpaRepository<TurnstileImportRow, UUID> {

    Page<TurnstileImportRow> findByBatchIdOrderByLineNumberAsc(UUID batchId, Pageable pageable);

    List<TurnstileImportRow> findByBatchIdAndRowStatusOrderByLineNumberAsc(UUID batchId, String rowStatus);

    int countByBatchIdAndRowStatus(UUID batchId, String rowStatus);
}
