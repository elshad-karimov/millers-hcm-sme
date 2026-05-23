package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compbenefits.domain.AllowanceType;

public interface AllowanceTypeRepository extends JpaRepository<AllowanceType, UUID> {

    boolean existsByCode(String code);

    List<AllowanceType> findByActiveTrueOrderByNameAsc();

    List<AllowanceType> findAllByOrderByNameAsc();
}
