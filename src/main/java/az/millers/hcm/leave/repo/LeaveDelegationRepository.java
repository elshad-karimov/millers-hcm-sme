package az.millers.hcm.leave.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.leave.domain.DelegationStatus;
import az.millers.hcm.leave.domain.LeaveDelegation;

public interface LeaveDelegationRepository extends JpaRepository<LeaveDelegation, UUID> {

    List<LeaveDelegation> findByLeaveRequestId(UUID leaveRequestId);

    @Query("SELECT d FROM LeaveDelegation d WHERE d.delegateId = :delegateId AND d.status = :status ORDER BY d.createdAt DESC")
    List<LeaveDelegation> findByDelegateIdAndStatus(@Param("delegateId") UUID delegateId,
                                                    @Param("status") DelegationStatus status);

    Optional<LeaveDelegation> findByLeaveRequestIdAndDelegateId(UUID leaveRequestId, UUID delegateId);
}
