package az.millers.hcm.organization.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.organization.domain.StructureVersion;
import az.millers.hcm.organization.domain.VersionStatus;

public interface StructureVersionRepository extends JpaRepository<StructureVersion, UUID> {

    @Query(value = "SELECT nextval('organization.structure_version_no_seq')", nativeQuery = true)
    long nextVersionNumber();

    Optional<StructureVersion> findFirstByStatus(VersionStatus status);

    List<StructureVersion> findAllByOrderByVersionNumberDesc();
}
