package az.millers.hcm.attendance.repo;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.attendance.domain.TurnstileImportBatch;

public interface TurnstileImportBatchRepository extends JpaRepository<TurnstileImportBatch, UUID> {

    Page<TurnstileImportBatch> findAllByOrderByImportedAtDesc(Pageable pageable);
}
