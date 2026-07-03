package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.KpiResult;

public interface KpiResultRepository extends JpaRepository<KpiResult, UUID> {

    List<KpiResult> findByAssignmentIdOrderByRecordedAtDesc(UUID assignmentId);
}
