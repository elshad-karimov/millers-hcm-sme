package az.millers.hcm.permission.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.permission.domain.PermissionType;

public interface PermissionTypeRepository extends JpaRepository<PermissionType, UUID> {

    Optional<PermissionType> findByCode(String code);

    boolean existsByCode(String code);

    List<PermissionType> findAllByOrderByNameAsc();

    List<PermissionType> findByActiveTrueOrderByNameAsc();
}
