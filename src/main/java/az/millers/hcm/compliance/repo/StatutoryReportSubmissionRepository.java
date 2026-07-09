package az.millers.hcm.compliance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compliance.domain.StatutoryReportSubmission;

public interface StatutoryReportSubmissionRepository extends JpaRepository<StatutoryReportSubmission, UUID> {

    List<StatutoryReportSubmission> findByTenantIdOrderByPeriodStartDesc(String tenantId);

    List<StatutoryReportSubmission> findByTenantIdAndTemplateIdOrderByPeriodStartDesc(String tenantId, UUID templateId);
}
