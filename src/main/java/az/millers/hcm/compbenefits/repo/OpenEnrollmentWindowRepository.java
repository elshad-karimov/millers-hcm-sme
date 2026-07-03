package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compbenefits.domain.OpenEnrollmentWindow;

public interface OpenEnrollmentWindowRepository extends JpaRepository<OpenEnrollmentWindow, UUID> {

    List<OpenEnrollmentWindow> findByTenantIdOrderByStartDateDesc(String tenantId);

    List<OpenEnrollmentWindow> findByTenantIdAndActiveTrueOrderByStartDateDesc(String tenantId);
}
