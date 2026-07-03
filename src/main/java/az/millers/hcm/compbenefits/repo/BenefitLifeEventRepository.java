package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compbenefits.domain.BenefitLifeEvent;

public interface BenefitLifeEventRepository extends JpaRepository<BenefitLifeEvent, UUID> {

    List<BenefitLifeEvent> findByTenantIdOrderByEventDateDesc(String tenantId);

    List<BenefitLifeEvent> findByTenantIdAndStatusOrderByEventDateDesc(String tenantId, String status);

    List<BenefitLifeEvent> findByTenantIdAndEmployeeIdOrderByEventDateDesc(String tenantId, UUID employeeId);
}
