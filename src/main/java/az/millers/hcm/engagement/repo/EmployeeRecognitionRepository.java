package az.millers.hcm.engagement.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.engagement.domain.EmployeeRecognition;
import az.millers.hcm.engagement.domain.RecognitionStatus;
import az.millers.hcm.engagement.domain.RecognitionVisibility;

/**
 * M478 — Recognition repository.
 */
public interface EmployeeRecognitionRepository extends JpaRepository<EmployeeRecognition, UUID> {

    @Query("SELECT r FROM EmployeeRecognition r " +
           "WHERE r.tenantId = :tenantId AND r.status = :status AND r.visibility = :visibility " +
           "ORDER BY r.createdAt DESC")
    List<EmployeeRecognition> findPublicWall(String tenantId, RecognitionStatus status,
                                             RecognitionVisibility visibility, Pageable pageable);

    List<EmployeeRecognition> findByTenantIdAndFromEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID fromEmployeeId);

    List<EmployeeRecognition> findByTenantIdAndToEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID toEmployeeId);
}
