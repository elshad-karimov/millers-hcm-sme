package az.millers.hcm.compliance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compliance.domain.ComplianceDeadline;

public interface ComplianceDeadlineRepository extends JpaRepository<ComplianceDeadline, UUID> {

    List<ComplianceDeadline> findByTenantIdAndActiveOrderByDueDayAsc(String tenantId, boolean active);
}
