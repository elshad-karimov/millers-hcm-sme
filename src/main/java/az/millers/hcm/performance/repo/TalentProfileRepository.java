package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.TalentProfile;

public interface TalentProfileRepository extends JpaRepository<TalentProfile, UUID> {

    Optional<TalentProfile> findByTenantIdAndEmployeeId(String tenantId, UUID employeeId);

    List<TalentProfile> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<TalentProfile> findByTenantIdAndHipoFlagTrueOrderByUpdatedAtDesc(String tenantId);

    List<TalentProfile> findByTenantIdAndRetentionRiskOrderByUpdatedAtDesc(String tenantId, String risk);
}
