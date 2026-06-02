package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.AssetStatus;
import az.millers.hcm.corehr.domain.EmployeeAsset;

public interface EmployeeAssetRepository
        extends JpaRepository<EmployeeAsset, UUID> {

    /** Most-recent first — the assignment journal for an employee. */
    List<EmployeeAsset> findByEmployeeIdOrderByAssignedAtDesc(UUID employeeId);

    /** Outstanding (still-assigned) assets — what offboarding clearance checks. */
    List<EmployeeAsset> findByEmployeeIdAndStatusOrderByAssignedAtDesc(UUID employeeId, AssetStatus status);

    long countByEmployeeIdAndStatus(UUID employeeId, AssetStatus status);
}
