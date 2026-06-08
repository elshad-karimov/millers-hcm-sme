package az.millers.hcm.organization.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.organization.domain.LegalEntity;

public interface LegalEntityRepository extends JpaRepository<LegalEntity, UUID> {

    List<LegalEntity> findAllByOrderByCodeAsc();

    List<LegalEntity> findByActiveTrueOrderByCodeAsc();

    Optional<LegalEntity> findByCode(String code);

    boolean existsByCode(String code);
}
