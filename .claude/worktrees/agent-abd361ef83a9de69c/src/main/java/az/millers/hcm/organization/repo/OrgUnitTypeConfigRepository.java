package az.millers.hcm.organization.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.organization.domain.OrgUnitTypeConfig;

public interface OrgUnitTypeConfigRepository extends JpaRepository<OrgUnitTypeConfig, String> {
    List<OrgUnitTypeConfig> findAllByOrderBySortOrderAscCodeAsc();
    List<OrgUnitTypeConfig> findByActiveTrueOrderBySortOrderAscCodeAsc();
    boolean existsByCode(String code);
}
