package az.millers.hcm.permission.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.permission.domain.PermissionBalance;

public interface PermissionBalanceRepository extends JpaRepository<PermissionBalance, UUID> {

    Optional<PermissionBalance> findByEmployeeIdAndPermissionTypeIdAndYear(
            UUID employeeId, UUID permissionTypeId, int year);

    List<PermissionBalance> findByEmployeeIdAndYearOrderByPermissionTypeId(UUID employeeId, int year);

    List<PermissionBalance> findByYearOrderByEmployeeIdAscPermissionTypeIdAsc(int year);
}
