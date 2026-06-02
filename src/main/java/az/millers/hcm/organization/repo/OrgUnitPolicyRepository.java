package az.millers.hcm.organization.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.organization.domain.OrgUnitPolicy;

public interface OrgUnitPolicyRepository extends JpaRepository<OrgUnitPolicy, UUID> {

    Optional<OrgUnitPolicy> findByOrgUnitIdAndVersionId(UUID orgUnitId, UUID versionId);

    List<OrgUnitPolicy> findByVersionId(UUID versionId);
}
