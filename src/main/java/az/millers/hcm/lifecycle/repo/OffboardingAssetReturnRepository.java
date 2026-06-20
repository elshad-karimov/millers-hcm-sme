package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.AssetReturnStatus;
import az.millers.hcm.lifecycle.domain.OffboardingAssetReturn;

public interface OffboardingAssetReturnRepository extends JpaRepository<OffboardingAssetReturn, UUID> {

    List<OffboardingAssetReturn> findByCaseIdOrderByAssetType(UUID caseId);

    Optional<OffboardingAssetReturn> findByCaseIdAndEmployeeAssetId(UUID caseId, UUID employeeAssetId);

    boolean existsByCaseIdAndReturnStatusNotIn(UUID caseId, List<AssetReturnStatus> statuses);
}
