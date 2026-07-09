package az.millers.hcm.compliance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compliance.domain.StatutoryReportTemplate;

public interface StatutoryReportTemplateRepository extends JpaRepository<StatutoryReportTemplate, UUID> {

    List<StatutoryReportTemplate> findByTenantIdAndActiveOrderByCodeAsc(String tenantId, boolean active);

    Optional<StatutoryReportTemplate> findByTenantIdAndCode(String tenantId, String code);
}
