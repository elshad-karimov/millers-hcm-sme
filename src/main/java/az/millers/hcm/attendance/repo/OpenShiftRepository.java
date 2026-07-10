package az.millers.hcm.attendance.repo;

import az.millers.hcm.attendance.domain.OpenShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * M482: Open shift repository.
 */
@Repository
public interface OpenShiftRepository extends JpaRepository<OpenShift, UUID> {

    List<OpenShift> findByTenantIdAndStatusOrderByShiftDateAsc(String tenantId, String status);

    List<OpenShift> findByTenantIdAndShiftDateBetweenOrderByShiftDateAsc(String tenantId, LocalDate from, LocalDate to);

    Optional<OpenShift> findByIdAndTenantId(UUID id, String tenantId);
}
