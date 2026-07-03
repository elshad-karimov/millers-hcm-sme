package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.PerfReviewTemplate;

public interface PerfReviewTemplateRepository extends JpaRepository<PerfReviewTemplate, UUID> {

    List<PerfReviewTemplate> findByTenantIdOrderByTemplateNameAsc(String tenantId);

    List<PerfReviewTemplate> findByTenantIdAndActiveTrueOrderByTemplateNameAsc(String tenantId);

    boolean existsByTenantIdAndTemplateCode(String tenantId, String templateCode);
}
