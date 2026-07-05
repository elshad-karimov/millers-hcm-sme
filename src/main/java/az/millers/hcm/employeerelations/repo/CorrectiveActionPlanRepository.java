package az.millers.hcm.employeerelations.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.employeerelations.domain.CorrectiveActionPlan;
import az.millers.hcm.employeerelations.domain.CorrectiveActionStatus;

/**
 * M447 — Corrective action plan repository.
 */
public interface CorrectiveActionPlanRepository extends JpaRepository<CorrectiveActionPlan, UUID> {

    Optional<CorrectiveActionPlan> findByIdAndTenantId(UUID id, String tenantId);

    List<CorrectiveActionPlan> findByTenantIdOrderByDueDateAsc(String tenantId);

    List<CorrectiveActionPlan> findByTenantIdAndStatusInOrderByDueDateAsc(String tenantId, List<CorrectiveActionStatus> statuses);

    List<CorrectiveActionPlan> findByTenantIdAndResponsibleUsernameOrderByDueDateAsc(String tenantId, String responsibleUsername);

    @Query("SELECT c FROM CorrectiveActionPlan c WHERE c.tenantId = :tenantId " +
           "AND c.status IN (:statuses) AND c.dueDate < :today")
    List<CorrectiveActionPlan> findOverdue(@Param("tenantId") String tenantId,
                                          @Param("statuses") List<CorrectiveActionStatus> statuses,
                                          @Param("today") LocalDate today);
}
