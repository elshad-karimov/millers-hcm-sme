package az.millers.hcm.corehr.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.corehr.domain.EmployeeStatusOverlay;
import az.millers.hcm.corehr.domain.EmployeeStatusOverlay.OverlaySource;
import az.millers.hcm.corehr.domain.EmploymentStatus;

public interface EmployeeStatusOverlayRepository
        extends JpaRepository<EmployeeStatusOverlay, UUID> {

    /** Full history — newest first. */
    List<EmployeeStatusOverlay> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);

    /** Currently-open overlays (effective_to IS NULL). */
    @Query("""
            select o from EmployeeStatusOverlay o
            where o.employeeId = :employeeId
              and o.effectiveTo is null
            order by o.status
            """)
    List<EmployeeStatusOverlay> findOpenForEmployee(UUID employeeId);

    /** The open overlay for a (employee, status) pair, if any. */
    @Query("""
            select o from EmployeeStatusOverlay o
            where o.employeeId = :employeeId
              and o.status = :status
              and o.effectiveTo is null
            """)
    Optional<EmployeeStatusOverlay> findOpenForEmployeeAndStatus(
            UUID employeeId, EmploymentStatus status);

    /** Find by source — used when a leave/BT row is cancelled and we need to close its overlay. */
    @Query("""
            select o from EmployeeStatusOverlay o
            where o.source = :source and o.sourceId = :sourceId
            """)
    List<EmployeeStatusOverlay> findBySource(OverlaySource source, UUID sourceId);
}
