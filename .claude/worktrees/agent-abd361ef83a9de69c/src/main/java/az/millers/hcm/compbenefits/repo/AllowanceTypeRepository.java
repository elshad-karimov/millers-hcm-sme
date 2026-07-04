package az.millers.hcm.compbenefits.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compbenefits.domain.AllowanceType;

public interface AllowanceTypeRepository extends JpaRepository<AllowanceType, UUID> {

    boolean existsByCode(String code);

    /**
     * M251 — Phase F.3 lookup: when a position profile item carries
     * a {@code reference_code} matching an AllowanceType.code, the
     * grant auto-creates the corresponding employee_allowance row.
     */
    Optional<AllowanceType> findByCode(String code);

    List<AllowanceType> findByActiveTrueOrderByNameAsc();

    List<AllowanceType> findAllByOrderByNameAsc();
}
