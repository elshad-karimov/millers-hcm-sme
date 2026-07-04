package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.HeadcountChangeRequest;

public interface HeadcountChangeRequestRepository
        extends JpaRepository<HeadcountChangeRequest, UUID> {

    List<HeadcountChangeRequest> findByPositionIdOrderByCreatedAtDesc(UUID positionId);

    Optional<HeadcountChangeRequest> findByWorkflowInstanceId(UUID workflowInstanceId);

    List<HeadcountChangeRequest> findByStatusOrderByCreatedAtDesc(String status);
}
