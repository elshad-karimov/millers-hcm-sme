package az.millers.hcm.permission.repo;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.permission.domain.PermissionRequest;
import az.millers.hcm.permission.domain.PermissionRequestStatus;

public interface PermissionRequestRepository extends JpaRepository<PermissionRequest, UUID> {

    @Query(value = "SELECT nextval('permission.permission_no_seq')", nativeQuery = true)
    long nextRequestNoSequence();

    Page<PermissionRequest> findByEmployeeIdOrderByPermissionDateDesc(UUID employeeId, Pageable pageable);

    Page<PermissionRequest> findByStatusOrderByPermissionDateDesc(PermissionRequestStatus status, Pageable pageable);

    Page<PermissionRequest> findAllByOrderByPermissionDateDesc(Pageable pageable);

    /** Scope-bounded equivalents used by ABAC-filtered lists (PRD 14.9). */
    Page<PermissionRequest> findByEmployeeIdInOrderByPermissionDateDesc(
            Collection<UUID> employeeIds, Pageable pageable);

    Page<PermissionRequest> findByEmployeeIdInAndStatusOrderByPermissionDateDesc(
            Collection<UUID> employeeIds, PermissionRequestStatus status, Pageable pageable);

    @Query("""
            select r from PermissionRequest r
            where r.employeeId = :employeeId
              and r.status = az.millers.hcm.permission.domain.PermissionRequestStatus.APPROVED
              and r.permissionDate between :rangeStart and :rangeEnd
            """)
    java.util.List<PermissionRequest> findApprovedInRange(
            UUID employeeId, java.time.LocalDate rangeStart, java.time.LocalDate rangeEnd);
}
