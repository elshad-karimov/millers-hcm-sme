package az.millers.hcm.ehs.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.ehs.domain.CorrectiveAction;
import az.millers.hcm.ehs.domain.CorrectiveActionStatus;

public interface CorrectiveActionRepository extends JpaRepository<CorrectiveAction, UUID> {

    Optional<CorrectiveAction> findByIdAndTenantId(UUID id, String tenantId);

    List<CorrectiveAction> findByTenantIdOrderByDueDateAsc(String tenantId);

    List<CorrectiveAction> findByTenantIdAndStatusOrderByDueDateAsc(String tenantId, CorrectiveActionStatus status);

    List<CorrectiveAction> findByTenantIdAndResponsibleUsernameOrderByDueDateAsc(String tenantId, String username);

    @Query("SELECT c FROM CorrectiveAction c WHERE c.tenantId = :tenant " +
           "AND c.status IN ('OPEN', 'IN_PROGRESS') AND c.dueDate < :today")
    List<CorrectiveAction> findOverdueActions(@Param("tenant") String tenantId, @Param("today") LocalDate today);
}
