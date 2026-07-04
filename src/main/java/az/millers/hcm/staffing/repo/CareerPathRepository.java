package az.millers.hcm.staffing.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.staffing.domain.CareerPath;

public interface CareerPathRepository extends JpaRepository<CareerPath, UUID> {
    List<CareerPath> findByTenantIdOrderByCodeAsc(String tenantId);
    List<CareerPath> findByTenantIdAndActiveTrueOrderByCodeAsc(String tenantId);
    Optional<CareerPath> findByTenantIdAndCode(String tenantId, String code);
}
