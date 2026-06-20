package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.ItAccessStatus;
import az.millers.hcm.lifecycle.domain.OffboardingItAccess;

public interface OffboardingItAccessRepository extends JpaRepository<OffboardingItAccess, UUID> {

    List<OffboardingItAccess> findByCaseIdOrderByAccessType(UUID caseId);

    Optional<OffboardingItAccess> findByCaseIdAndAccessType(UUID caseId, String accessType);

    boolean existsByCaseIdAndAccessStatusNotIn(UUID caseId, List<ItAccessStatus> statuses);
}
