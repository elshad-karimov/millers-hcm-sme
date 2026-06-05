package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.corehr.domain.AssetStatus;
import az.millers.hcm.corehr.domain.AssetType;
import az.millers.hcm.corehr.domain.EmployeeAsset;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeAssetRepository
        extends JpaRepository<EmployeeAsset, UUID> {

    /** Most-recent first — the assignment journal for an employee. */
    List<EmployeeAsset> findByEmployeeIdOrderByAssignedAtDesc(UUID employeeId);

    /** Outstanding (still-assigned) assets — what offboarding clearance checks. */
    List<EmployeeAsset> findByEmployeeIdAndStatusOrderByAssignedAtDesc(UUID employeeId, AssetStatus status);

    long countByEmployeeIdAndStatus(UUID employeeId, AssetStatus status);

    /**
     * M124 — admin cross-employee search. All filters optional; nulls are
     * ignored so the SPA can mix and match.
     */
    @Query("""
        SELECT a FROM EmployeeAsset a
         WHERE (:status     IS NULL OR a.status     = :status)
           AND (:assetType  IS NULL OR a.assetType  = :assetType)
           AND (:employeeId IS NULL OR a.employeeId = :employeeId)
         ORDER BY a.assignedAt DESC
        """)
    List<EmployeeAsset> search(@Param("status") AssetStatus status,
                                @Param("assetType") AssetType assetType,
                                @Param("employeeId") UUID employeeId);

    long countByStatus(AssetStatus status);
}
